package com.example.clandestinely.ui.tips;

import android.app.Application;

import androidx.annotation.NonNull;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Picks the tips shown on the Tips screen and the Home tip card (M3, FR3.1–3.3).
 *
 * Inputs, all filled in automatically:
 * - weather states: from CurrentWeatherStates (M1 updates it)
 * - sensitivities and language: from the user's profile (users/{uid})
 * - time slot: from the phone's clock
 *
 * Week 6 uses the rule-based library only. Week 7 adds the tips_cache + Gemini steps before it.
 */
public class TipsViewModel extends AndroidViewModel {

    /** Everything a screen needs to draw the tips. */
    public static final class TipsState {
        public final List<String> states;
        public final List<RuleTip> topTips;
        public final List<RuleTip> moreTips;
        public final String language;
        public final boolean liveWeather;

        TipsState(List<String> states, List<RuleTip> topTips, List<RuleTip> moreTips,
                  String language, boolean liveWeather) {
            this.states = states;
            this.topTips = topTips;
            this.moreTips = moreTips;
            this.language = language;
            this.liveWeather = liveWeather;
        }
    }

    /** How many extra tips the "More tips" section shows at most. */
    private static final int MAX_MORE_TIPS = 12;

    private final RuleTipLibrary library;
    private final MutableLiveData<TipsState> state = new MutableLiveData<>();

    private List<String> currentStates = Collections.singletonList(Schema.STATE_NORMAL);
    private List<String> sensitivities = Collections.emptyList();
    private String language = "en";

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

        state.setValue(new TipsState(currentStates, top, more, language,
                CurrentWeatherStates.hasLiveWeather()));
    }

    /** Reads sensitivities and language from the profile. Guests or offline: tips for everyone, English. */
    private void loadProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            refresh();
            return;
        }
        new UserRepository().getProfile(user.getUid()).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                UserProfile profile = task.getResult();
                Health health = profile.getHealth();
                if (health != null) sensitivities = TipCacheKey.sensitivitiesOf(health);
                if (profile.getSettings() != null && profile.getSettings().getLanguage() != null) {
                    language = profile.getSettings().getLanguage();
                }
            }
            refresh();
        });
    }

    @Override
    protected void onCleared() {
        CurrentWeatherStates.get().removeObserver(weatherObserver);
    }
}