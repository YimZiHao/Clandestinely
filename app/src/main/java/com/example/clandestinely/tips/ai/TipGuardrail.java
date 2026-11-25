package com.example.clandestinely.tips.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.weather.WeatherClassifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
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
 *    unless a negation ("avoid", "never", "jangan", ...) comes right before it, in the same clause.
 * 6. On topic: it must mention the most severe active state (e.g. lightning / thunder for a storm).
 *    Whole words only, so "sun" doesn't match "Sunday".
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
    /** 7+ digits, with at most one space or hyphen between digits ("03-7967 3200"). Not "33 - 35". */
    private static final Pattern PHONE = Pattern.compile("\\+?\\d(?:[\\s-]?\\d){6,}");
    /** End of a sentence: . ! ? followed by a space or the end, so "34.5°C" is one sentence. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?=\\s|$)");
    /** Things that start a new clause; a negation before one of these doesn't count. */
    private static final Pattern CLAUSE_BREAK = Pattern.compile(
            "[,;:.!?]|\\b(but|just|then|so|tapi|tetapi|cuma|hanya|jadi)\\b");
    /** How many words may sit between a negation and the unsafe phrase ("never WAIT under a tree"). */
    private static final int MAX_WORDS_AFTER_NEGATION = 2;
    private static final Pattern DOSE = Pattern.compile(
            "\\b\\d+(\\.\\d+)?\\s?(mg|mcg|ml|g)\\b|\\bdos(e|es|age|is)\\b|diagnos|prescri|preskripsi",
            Pattern.CASE_INSENSITIVE);

    /** Words that cancel an unsafe phrase when they come right before it (same clause). */
    private static final List<String> NEGATIONS = Arrays.asList(
            "avoid", "don't", "dont", "do not", "never", "no", "not", "skip", "postpone",
            "stay away from", "instead of", "rather than",
            "jangan", "elak", "elakkan", "tangguh", "tangguhkan", "bukan", "tidak", "usah");

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
                "lightning", "thunder", "thunderstorm", "thunderstorms", "storm", "storms",
                "indoors", "inside", "shelter",
                "petir", "kilat", "guruh", "ribut", "dalam bangunan", "berteduh"));
        TOPIC.put(Schema.STATE_HAZE_UNHEALTHY, Arrays.asList(
                "haze", "hazy", "air", "aqi", "mask", "masks", "smoke", "indoors", "inside",
                "breath", "breathe", "breathing",
                "jerebu", "udara", "pelitup", "topeng", "dalam bangunan", "nafas", "bernafas"));
        TOPIC.put(Schema.STATE_HAZE_SENSITIVE, TOPIC.get(Schema.STATE_HAZE_UNHEALTHY));
        TOPIC.put(Schema.STATE_RAIN, Arrays.asList(
                "rain", "raining", "rainy", "rainfall", "umbrella", "umbrellas", "wet",
                "slip", "slippery", "slipping", "flood", "floods", "flooded", "flooding",
                "puddle", "puddles", "raincoat", "shower", "showers", "downpour",
                "hujan", "kehujanan", "payung", "basah", "licin", "banjir", "lopak", "baju hujan"));
        TOPIC.put(Schema.STATE_HEAT, Arrays.asList(
                "heat", "hot", "water", "hydrate", "hydrated", "hydrating", "hydration",
                "drink", "drinking", "shade", "shaded", "shady", "sun", "sunny", "sunlight",
                "cool", "cooler", "cooling",
                "panas", "kepanasan", "air", "minum", "teduh", "matahari", "sejuk"));
        TOPIC.put(Schema.STATE_UV, Arrays.asList(
                "sun", "sunny", "sunlight", "sunburn", "uv", "shade", "shaded", "shady",
                "sunscreen", "sunblock", "hat", "cap", "sunglasses", "umbrella",
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

        String main = WeatherClassifier.mostSevere(states);
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
        String s = text.replace('\n', ' ').replace('\r', ' ').replace('\u2019', '\'')
                .replace("**", "").replace("`", "").replace("#", "")
                .replaceAll("\\s+", " ").trim();
        while (s.length() >= 2 && isQuote(s.charAt(0)) && isQuote(s.charAt(s.length() - 1))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    static int sentenceCount(String tip) {
        int count = 0;
        for (String part : SENTENCE_END.split(tip)) {
            if (!part.trim().isEmpty()) count++;
        }
        return count;
    }

    /**
     * True if the phrase appears (at the start of a word) without a negation right before it.
     * "Right before" = in the same clause, with at most MAX_WORDS_AFTER_NEGATION words in between:
     * "never wait under a tree" is negated, "No need to run, just wait under a tree" is not.
     */
    static boolean containsUnnegated(String lower, String phrase) {
        Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase)).matcher(lower);
        while (m.find()) {
            if (!negatedBefore(lower.substring(0, m.start()))) return true;
        }
        return false;
    }

    /** Is there a negation at the end of this text (same clause, at most 2 words after it)? */
    static boolean negatedBefore(String before) {
        // Keep only the current clause.
        Matcher breaks = CLAUSE_BREAK.matcher(before);
        int clauseStart = 0;
        while (breaks.find()) clauseStart = breaks.end();
        String clause = before.substring(clauseStart).trim();
        if (clause.isEmpty()) return false;

        String[] words = clause.split("\\s+");
        for (String negation : NEGATIONS) {
            String[] neg = negation.split(" ");
            // The negation may end at most MAX_WORDS_AFTER_NEGATION words before the phrase.
            for (int gap = 0; gap <= MAX_WORDS_AFTER_NEGATION; gap++) {
                int end = words.length - gap;          // exclusive end of the negation words
                int start = end - neg.length;
                if (start < 0) continue;
                boolean match = true;
                for (int i = 0; i < neg.length; i++) {
                    if (!words[start + i].equals(neg[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) return true;
            }
        }
        return false;
    }

    /** True if any of the words / phrases appears as a whole word ("sun" not in "Sunday"). */
    static boolean containsAny(String lower, List<String> words) {
        for (String w : words) {
            if (Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(w) + "(?![\\p{L}\\p{N}])")
                    .matcher(lower).find()) {
                return true;
            }
        }
        return false;
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