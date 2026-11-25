package com.example.clandestinely.tips.ai;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.model.TipCacheEntry;
import com.example.clandestinely.data.model.TipCacheKey;
import com.example.clandestinely.data.repository.TipCacheRepository;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.HarmBlockThreshold;
import com.google.firebase.ai.type.HarmCategory;
import com.google.firebase.ai.type.SafetySetting;
import com.google.firebase.ai.type.ThinkingConfig;
import com.google.firebase.ai.type.ThinkingLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Smart tips (M3, Week 7, FR3.1–3.3): tips_cache → Gemini (Firebase AI Logic) → rule library.
 *
 * 1. Memory: the same request in the last hour reuses the answer (no network).
 * 2. tips_cache: a fresh English AI tip saved by another user with the same weather / time / sensitivities.
 * 3. Gemini: if signed in (not a guest), ask Gemini. 10 second timeout.
 * 4. Every AI tip (fresh or cached) must pass TipGuardrail.
 * 5. Anything fails → the callback gets null and the screen keeps the rule-based tip.
 *
 * Shared by the Home card and the Tips screen, so they never ask Gemini twice for the same thing.
 */
public final class AiTipRepository {

    private static final String TAG = "AiTipRepository";

    /** Gemini model. If Google retires it, change this one line (see docs/ai_tip_prompt_design.md). */
    public static final String MODEL_NAME = "gemini-3.5-flash";
    /** Saved in tips_cache.model (max 60 characters). */
    public static final String MODEL_LABEL = MODEL_NAME + " (Firebase AI Logic) " + AiTipPrompt.PROMPT_VERSION;

    private static final long TIMEOUT_SECONDS = 10;
    /** After a failure, wait this long before asking Gemini again for the same thing. */
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000L;

    /** A tip that passed the guardrail. */
    public static final class AiTip {
        public final String text;
        /** True if it came from tips_cache rather than a new Gemini call. */
        public final boolean fromCache;

        AiTip(String text, boolean fromCache) {
            this.text = text;
            this.fromCache = fromCache;
        }
    }

    public interface Callback {
        /** Called once. {@code tip} is null when the rule-based tip should be used. May run on any thread. */
        void onResult(@Nullable AiTip tip);
    }

    private static final class Memo {
        @Nullable final AiTip tip;
        final long until;

        Memo(@Nullable AiTip tip, long until) {
            this.tip = tip;
            this.until = until;
        }
    }

    private static volatile AiTipRepository instance;

    public static AiTipRepository get() {
        if (instance == null) {
            synchronized (AiTipRepository.class) {
                if (instance == null) instance = new AiTipRepository();
            }
        }
        return instance;
    }

    private final Map<String, Memo> memo = new ConcurrentHashMap<>();
    private final Map<String, List<Callback>> waiting = new HashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private TipCacheRepository cache;      // created on first use, so Firestore isn't touched early
    private GenerativeModelFutures model;  // created on first use

    private AiTipRepository() {
    }

    /** The tip we already have for this request, without any network call. Null if none. */
    @Nullable
    public AiTip peek(@NonNull TipRequest r) {
        Memo m = memo.get(memoKey(r));
        return m != null && m.until > System.currentTimeMillis() ? m.tip : null;
    }

    /** Gets a smart tip for this request. The callback runs once, with null if the rule tip should be used. */
    public void request(@NonNull TipRequest r, @NonNull Callback callback) {
        final String key;
        try {
            key = TipCacheKey.build(r.states, r.timeSlot, r.sensitivities);
        } catch (IllegalArgumentException e) {
            callback.onResult(null);
            return;
        }
        final String memoKey = memoKey(key, r);

        Memo m = memo.get(memoKey);
        if (m != null && m.until > System.currentTimeMillis()) {
            callback.onResult(m.tip);
            return;
        }

        synchronized (waiting) {
            List<Callback> list = waiting.get(memoKey);
            if (list != null) {          // the same request is already on its way
                list.add(callback);
                return;
            }
            list = new ArrayList<>();
            list.add(callback);
            waiting.put(memoKey, list);
        }

        // Only English, real-weather tips are shared through tips_cache (the cache key has no language).
        boolean useCache = "en".equals(r.language) && !r.preview;
        if (!useCache) {
            askGemini(r, key, memoKey, false);
            return;
        }
        cache().getFreshTip(key).addOnCompleteListener(task -> {
            TipCacheEntry entry = task.isSuccessful() ? task.getResult() : null;
            if (entry != null && Schema.SOURCE_AI.equals(entry.getSource())) {
                TipGuardrail.Result check = TipGuardrail.check(entry.getText(), r.states);
                if (check.ok) {
                    finish(memoKey, new AiTip(check.text, true), System.currentTimeMillis()
                            + TipCacheKey.CACHE_LIFETIME_MS);
                    return;
                }
                Log.w(TAG, "Cached tip rejected: " + check.reason);
            }
            askGemini(r, key, memoKey, true);
        });
    }

    private void askGemini(TipRequest r, String key, String memoKey, boolean saveToCache) {
        if (r.guest) {                         // guests: rule library only
            finish(memoKey, null, System.currentTimeMillis() + RETRY_AFTER_MS);
            return;
        }
        final ListenableFuture<GenerateContentResponse> call;
        try {
            Content prompt = new Content.Builder().addText(AiTipPrompt.userPrompt(r)).build();
            call = Futures.withTimeout(model().generateContent(prompt), TIMEOUT_SECONDS, TimeUnit.SECONDS, timer);
        } catch (RuntimeException e) {         // e.g. Firebase AI Logic not set up
            Log.w(TAG, "Gemini not available: " + e.getClass().getSimpleName());
            finish(memoKey, null, System.currentTimeMillis() + RETRY_AFTER_MS);
            return;
        }

        Futures.addCallback(call, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse response) {
                // Anything that throws here (e.g. getText() on an empty reply) must still call
                // finish(), or later requests for the same weather would wait forever.
                TipGuardrail.Result check;
                try {
                    String tip = AiTipPrompt.parseTip(response.getText());
                    check = TipGuardrail.check(tip, r.states);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Could not read Gemini reply: " + e.getClass().getSimpleName());
                    finish(memoKey, null, System.currentTimeMillis() + RETRY_AFTER_MS);
                    return;
                }
                if (!check.ok) {
                    Log.w(TAG, "Gemini tip rejected: " + check.reason);
                    finish(memoKey, null, System.currentTimeMillis() + RETRY_AFTER_MS);
                    return;
                }
                finish(memoKey, new AiTip(check.text, false),
                        System.currentTimeMillis() + TipCacheKey.CACHE_LIFETIME_MS);
                if (saveToCache) {
                    cache().saveTip(r.states, r.timeSlot, r.sensitivities, check.text,
                                    Schema.SOURCE_AI, MODEL_LABEL)
                            .addOnFailureListener(e -> Log.w(TAG, "Could not save tip: " + e.getMessage()));
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                // Offline, timeout, quota, blocked by safety filters, App Check, ...
                Log.w(TAG, "Gemini failed: " + t.getClass().getSimpleName());
                finish(memoKey, null, System.currentTimeMillis() + RETRY_AFTER_MS);
            }
        }, MoreExecutors.directExecutor());
    }

    private void finish(String memoKey, @Nullable AiTip tip, long until) {
        memo.put(memoKey, new Memo(tip, until));
        List<Callback> callbacks;
        synchronized (waiting) {
            callbacks = waiting.remove(memoKey);
        }
        if (callbacks == null) return;
        for (Callback c : callbacks) {
            try {
                c.onResult(tip);
            } catch (RuntimeException e) {   // one bad screen mustn't stop the others getting a result
                Log.w(TAG, "Tip callback failed: " + e.getClass().getSimpleName());
            }
        }
    }

    private static String memoKey(TipRequest r) {
        String key;
        try {
            key = TipCacheKey.build(r.states, r.timeSlot, r.sensitivities);
        } catch (IllegalArgumentException e) {
            key = "invalid";
        }
        return memoKey(key, r);
    }

    /** Same as memoKey(r), for callers that already built the tips_cache key. */
    private static String memoKey(String key, TipRequest r) {
        return key + "|" + r.language + (r.preview ? "|preview" : "") + (r.guest ? "|guest" : "");
    }

    private synchronized TipCacheRepository cache() {
        if (cache == null) cache = new TipCacheRepository();
        return cache;
    }

    private synchronized GenerativeModelFutures model() {
        if (model == null) {
            Map<String, com.google.firebase.ai.type.Schema> fields = new HashMap<>();
            fields.put("tip", com.google.firebase.ai.type.Schema.str(
                    "One weather safety tip, at most 2 sentences and 160 characters."));
            GenerationConfig config = new GenerationConfig.Builder()
                    .setResponseMimeType("application/json")
                    .setResponseSchema(com.google.firebase.ai.type.Schema.obj(fields))
                    .setMaxOutputTokens(1024)
                    .setThinkingConfig(new ThinkingConfig.Builder()
                            .setThinkingLevel(ThinkingLevel.LOW)
                            .build())
                    .build();

            List<SafetySetting> safety = Arrays.asList(
                    new SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.LOW_AND_ABOVE, null),
                    new SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.LOW_AND_ABOVE, null),
                    new SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.LOW_AND_ABOVE, null),
                    new SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE, null));

            Content system = new Content.Builder().addText(AiTipPrompt.SYSTEM_INSTRUCTION).build();

            GenerativeModel gemini = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel(MODEL_NAME, config, safety,
                            /* tools */ null, /* toolConfig */ null, system);
            model = GenerativeModelFutures.from(gemini);
        }
        return model;
    }
}