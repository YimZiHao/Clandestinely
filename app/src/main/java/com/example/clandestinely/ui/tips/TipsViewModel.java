package com.example.clandestinely.ui.tips;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.clandestinely.data.model.HazardExpiry;
import com.example.clandestinely.data.model.Health;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.model.TipCacheKey;
import com.example.clandestinely.data.model.UserProfile;
import com.example.clandestinely.data.repository.CurrentWeatherStates;
import com.example.clandestinely.data.repository.UserRepository;
import com.example.clandestinely.tips.RuleTip;
import com.example.clandestinely.tips.RuleTipLibrary;
import com.example.clandestinely.tips.RuleTipLoader;
import com.example.clandestinely.tips.ai.AiTipPrompt;
import com.example.clandestinely.tips.ai.AiTipRepository;
import com.example.clandestinely.tips.ai.TipRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Picks the tips shown on the Tips screen and the Home tip card (M3, FR3.1–3.3).
 *
 * Inputs, all filled in automatically:
 * - weather states and numbers: from CurrentWeatherStates (M1 updates it)
 * - sensitivities and language: from the user's profile (users/{uid})
 * - time: from the phone's clock (Malaysia time)
 *
 * Rule-based tips are shown straight away. The smart tip (tips_cache → Gemini) is added when it
 * arrives. If it never does (offline, guest, error, failed guardrail), the rule tips stay.
 */
public class TipsViewModel extends AndroidViewModel {

    /** Everything a screen needs to draw the tips. */
    public static final class TipsState {
        public final List<String> states;
        public final List<RuleTip> topTips;
        public final List<RuleTip> moreTips;
        public final String language;
        public final boolean liveWeather;
        /** The smart (AI) tip, or null to use topTips.get(0). */
        @Nullable public final AiTipRepository.AiTip aiTip;

        TipsState(List<String> states, List<RuleTip> topTips, List<RuleTip> moreTips,
                  String language, boolean liveWeather, @Nullable AiTipRepository.AiTip aiTip) {
            this.states = states;
            this.topTips = topTips;
            this.moreTips = moreTips;
            this.language = language;
            this.liveWeather = liveWeather;
            this.aiTip = aiTip;
        }
    }

    /** How many extra tips the "More tips" section shows at most. */
    private static final int MAX_MORE_TIPS = 12;

    private final RuleTipLibrary library;
    private final AiTipRepository aiTips = AiTipRepository.get();
    private final MutableLiveData<TipsState> state = new MutableLiveData<>();

    private List<String> currentStates = Collections.singletonList(Schema.STATE_NORMAL);
    private List<String> sensitivities = Collections.emptyList();
    private String language = "en";
    private boolean guest = true;
    /** Don't ask Gemini until the profile is read, or the first call would ignore sensitivities. */
    private boolean profileLoaded = false;
    /** Counts refreshes, so an old smart-tip answer can't overwrite newer weather. */
    private volatile int generation = 0;

    private final Observer<List<String>> weatherObserver = states -> {
        currentStates = states;
        refresh();
    };

    public TipsViewModel(@NonNull Application application) {
        super(application);
        library = RuleTipLoader.get(application);
        CurrentWeatherStates.get().observeForever(weatherObserver);
        loadProfile();
    }

    public LiveData<TipsState> getState() {
        return state;
    }

    /** Picks the tips again. Call it when a screen is shown, since the time of day may have changed. */
    public void refresh() {
        long now = System.currentTimeMillis();
        String slot = TipCacheKey.timeSlotAt(now);
        long hour = now / HazardExpiry.HOUR_MS;

        List<RuleTip> top = library.select(currentStates, sensitivities, slot,
                RuleTipLibrary.DEFAULT_MAX_TIPS, hour);
        List<RuleTip> more = new ArrayList<>(library.select(currentStates, sensitivities, slot,
                top.size() + MAX_MORE_TIPS, hour));
        more.removeAll(top);

        final List<String> states = currentStates;
        final String lang = language;
        final boolean live = CurrentWeatherStates.hasLiveWeather();
        final int thisGeneration = ++generation;

        TipRequest request = buildRequest(states, slot, now);
        // Show rule tips now (plus a smart tip if we already have one for this exact request).
        state.setValue(new TipsState(states, top, more, lang, live, aiTips.peek(request)));

        if (!profileLoaded) return;
        aiTips.request(request, tip -> {
            if (tip == null || thisGeneration != generation) return; // keep the rule tips
            state.postValue(new TipsState(states, top, more, lang, live, tip));
        });
    }

    private TipRequest buildRequest(List<String> states, String slot, long now) {
        boolean preview = CurrentWeatherStates.isPreview();
        SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.US);
        clock.setTimeZone(HazardExpiry.MALAYSIA);
        return new TipRequest(states, sensitivities, slot, clock.format(new Date(now)), language,
                preview ? null : CurrentWeatherStates.latestWeather(),
                preview ? null : AiTipPrompt.nextHours(CurrentWeatherStates.hourly()),
                guest, preview);
    }

    /** Reads sensitivities and language from the profile. Guests or offline: tips for everyone, English. */
    private void loadProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            profileLoaded = true;
            refresh();
            return;
        }
        guest = user.isAnonymous();
        new UserRepository().getProfile(user.getUid()).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                UserProfile profile = task.getResult();
                Health health = profile.getHealth();
                if (health != null) sensitivities = TipCacheKey.sensitivitiesOf(health);
                if (profile.getSettings() != null && profile.getSettings().getLanguage() != null) {
                    language = profile.getSettings().getLanguage();
                }
            }
            profileLoaded = true;
            refresh();
        });
    }

    @Override
    protected void onCleared() {
        CurrentWeatherStates.get().removeObserver(weatherObserver);
    }
}