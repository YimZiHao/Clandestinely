package com.example.clandestinely.tips.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.tips.RuleTipLibrary;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks an AI tip before anyone sees it (M3, Week 7). If a check fails, the app shows the
 * rule-based tip instead. Runs on fresh Gemini replies AND on tips read from tips_cache,
 * because any signed-in user can write to the cache.
 *
 * Checks, in order:
 * 1. Clean-up: trim, drop quotes / markdown, squash spaces.
 * 2. Length: 15–220 characters, at most 3 sentences.
 * 3. No links, emails or phone numbers (999 is allowed).
 * 4. No medical dosing or diagnosis words.
 * 5. No unsafe advice for the active weather (e.g. "shelter under a tree" in a thunderstorm),
 *    unless it is negated ("avoid", "don't", "jangan", ...).
 * 6. On topic: it must mention the most severe active state (e.g. lightning / thunder for a storm).
 *
 * English and Bahasa Melayu word lists. Pure Java, so it is covered by unit tests.
 */
public final class TipGuardrail {

    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 220;
    public static final int MAX_SENTENCES = 3;

    /** The outcome of a check. */
    public static final class Result {
        public final boolean ok;
        /** The cleaned tip text (only meaningful when ok). */
        @Nullable public final String text;
        /** Why it failed, for the log. Null when ok. */
        @Nullable public final String reason;

        private Result(boolean ok, @Nullable String text, @Nullable String reason) {
            this.ok = ok;
            this.text = text;
            this.reason = reason;
        }

        static Result pass(String text) {
            return new Result(true, text, null);
        }

        static Result fail(String reason) {
            return new Result(false, null, reason);
        }
    }

    private static final Pattern LINK = Pattern.compile(
            "https?://|www\\.|\\.(com|my|net|org|io)\\b|@\\w", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{5,}\\d");
    private static final Pattern DOSE = Pattern.compile(
            "\\b\\d+(\\.\\d+)?\\s?(mg|mcg|ml|g)\\b|\\bdos(e|es|age|is)\\b|diagnos|prescri|preskripsi",
            Pattern.CASE_INSENSITIVE);

    /** Words that cancel an unsafe phrase when they come just before it. */
    private static final List<String> NEGATIONS = Arrays.asList(
            "avoid", "don't", "dont", "do not", "never", "no ", "not ", "skip", "postpone",
            "stay away", "instead of", "rather than", "stop",
            "jangan", "elak", "tangguh", "bukan", "tidak", "usah");

    /** Unsafe advice per weather state. */
    private static final Map<String, List<String>> UNSAFE = new HashMap<>();
    /** Words that show the tip is about that state. */
    private static final Map<String, List<String>> TOPIC = new HashMap<>();

    static {
        UNSAFE.put(Schema.STATE_THUNDERSTORM, Arrays.asList(
                "under a tree", "under the tree", "under trees", "under tall tree",
                "open field", "swim", "safe to go outside", "fine to go outside", "golf",
                "bawah pokok", "padang terbuka", "berenang", "selamat untuk keluar"));
        UNSAFE.put(Schema.STATE_HAZE_UNHEALTHY, Arrays.asList(
                "outdoor exercise", "exercise outdoors", "exercise outside", "go for a run",
                "go jogging", "jog outside", "open the window", "open your window",
                "senaman luar", "bersenam di luar", "berjoging", "buka tingkap"));
        UNSAFE.put(Schema.STATE_RAIN, Arrays.asList(
                "walk through flood", "wade", "drive through flood", "swim",
                "redah banjir", "meredah", "berenang"));
        UNSAFE.put(Schema.STATE_HEAT, Arrays.asList(
                "skip water", "skip drinking", "midday run", "run at noon",
                "tak perlu minum"));

        TOPIC.put(Schema.STATE_THUNDERSTORM, Arrays.asList(
                "lightning", "thunder", "storm", "indoors", "inside",
                "petir", "kilat", "guruh", "ribut", "dalam bangunan", "berteduh"));
        TOPIC.put(Schema.STATE_HAZE_UNHEALTHY, Arrays.asList(
                "haze", "air", "aqi", "mask", "smoke", "indoors", "inside", "breath",
                "jerebu", "udara", "pelitup", "topeng", "dalam bangunan", "nafas"));
        TOPIC.put(Schema.STATE_HAZE_SENSITIVE, TOPIC.get(Schema.STATE_HAZE_UNHEALTHY));
        TOPIC.put(Schema.STATE_RAIN, Arrays.asList(
                "rain", "umbrella", "wet", "slip", "flood", "puddle", "raincoat", "shower",
                "hujan", "payung", "basah", "licin", "banjir", "lopak", "baju hujan"));
        TOPIC.put(Schema.STATE_HEAT, Arrays.asList(
                "heat", "hot", "water", "hydrat", "drink", "shade", "sun", "cool",
                "panas", "air", "minum", "teduh", "matahari", "sejuk"));
        TOPIC.put(Schema.STATE_UV, Arrays.asList(
                "sun", "uv", "shade", "sunscreen", "hat", "sunglasses", "umbrella",
                "matahari", "teduh", "pelindung", "topi", "cermin mata hitam", "payung"));
    }

    private TipGuardrail() {
    }

    /**
     * @param text   the tip from Gemini or tips_cache
     * @param states the active weather states the tip is meant for
     */
    @NonNull
    public static Result check(@Nullable String text, @NonNull List<String> states) {
        String tip = clean(text);
        if (tip.isEmpty()) return Result.fail("empty");
        if (tip.length() < MIN_LENGTH) return Result.fail("too short");
        if (tip.length() > MAX_LENGTH) return Result.fail("too long");
        if (sentenceCount(tip) > MAX_SENTENCES) return Result.fail("too many sentences");
        if (LINK.matcher(tip).find()) return Result.fail("link or email");
        if (PHONE.matcher(tip.replace("999", "")).find()) return Result.fail("phone number");
        if (DOSE.matcher(tip).find()) return Result.fail("medical dosing or diagnosis");

        String lower = tip.toLowerCase(Locale.ROOT);
        for (String state : states) {
            List<String> unsafe = UNSAFE.get(state);
            if (unsafe == null) continue;
            for (String phrase : unsafe) {
                if (containsUnnegated(lower, phrase)) {
                    return Result.fail("unsafe for " + state + ": " + phrase);
                }
            }
        }

        String main = mostSevere(states);
        List<String> topic = TOPIC.get(main);
        if (topic != null && !containsAny(lower, topic)) {
            return Result.fail("off topic for " + main);
        }
        return Result.pass(tip);
    }

    /** Trim, remove wrapping quotes and markdown, squash whitespace. */
    @NonNull
    static String clean(@Nullable String text) {
        if (text == null) return "";
        String s = text.replace('\n', ' ').replace('\r', ' ')
                .replace("**", "").replace("`", "").replace("#", "")
                .replaceAll("\\s+", " ").trim();
        while (s.length() >= 2 && isQuote(s.charAt(0)) && isQuote(s.charAt(s.length() - 1))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    static int sentenceCount(String tip) {
        int count = 0;
        for (String part : tip.split("[.!?]+")) {
            if (!part.trim().isEmpty()) count++;
        }
        return count;
    }

    /** True if the phrase appears somewhere without a negation word just before it. */
    static boolean containsUnnegated(String lower, String phrase) {
        int from = 0;
        while (true) {
            int at = lower.indexOf(phrase, from);
            if (at < 0) return false;
            String before = lower.substring(Math.max(0, at - 30), at);
            boolean negated = false;
            for (String n : NEGATIONS) {
                if (before.contains(n)) {
                    negated = true;
                    break;
                }
            }
            if (!negated) return true;
            from = at + phrase.length();
        }
    }

    private static boolean containsAny(String lower, List<String> words) {
        for (String w : words) {
            if (lower.contains(w)) return true;
        }
        return false;
    }

    private static String mostSevere(List<String> states) {
        for (String s : RuleTipLibrary.SEVERITY_ORDER) {
            if (states.contains(s)) return s;
        }
        return Schema.STATE_NORMAL;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '“' || c == '”';
    }

    /** For tests. */
    static List<String> unsafePhrases(String state) {
        List<String> list = UNSAFE.get(state);
        return list == null ? Collections.<String>emptyList() : list;
    }
}