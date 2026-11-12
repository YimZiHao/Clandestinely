package com.example.clandestinely.ui.tips;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.repository.CurrentWeatherStates;
import com.example.clandestinely.tips.RuleTip;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tips screen (M3): current weather, the top tips for this user, and more tips for this weather.
 * Opened from the Home tip card.
 *
 * In debug builds it also shows "Preview weather" chips, so the screen can be tested and demoed
 * before the weather module (M1) is connected.
 */
public class TipsActivity extends AppCompatActivity {

    private TipsViewModel viewModel;
    private ChipGroup stateChips;
    private TextView note;
    private LinearLayout topList;
    private LinearLayout moreList;
    private TextView moreHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tips);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tips_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.tips_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        stateChips = findViewById(R.id.tips_state_chips);
        note = findViewById(R.id.tips_note);
        topList = findViewById(R.id.tips_top_list);
        moreList = findViewById(R.id.tips_more_list);
        moreHeader = findViewById(R.id.tips_more_header);

        if (isDebugBuild()) {
            setUpPreview();
        } else {
            findViewById(R.id.tips_preview_section).setVisibility(View.GONE);
        }

        viewModel = new ViewModelProvider(this).get(TipsViewModel.class);
        viewModel.getState().observe(this, this::show);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void show(TipsViewModel.TipsState s) {
        stateChips.removeAllViews();
        for (String state : s.states) {
            Chip chip = new Chip(this);
            chip.setText(WeatherStateLabels.state(this, state));
            chip.setClickable(false);
            chip.setCheckable(false);
            stateChips.addView(chip);
        }

        note.setText(s.liveWeather ? R.string.tips_note_live : R.string.tips_note_waiting);

        fill(topList, s.topTips, s.language);
        fill(moreList, s.moreTips, s.language);
        int moreVisibility = s.moreTips.isEmpty() ? View.GONE : View.VISIBLE;
        moreHeader.setVisibility(moreVisibility);
        moreList.setVisibility(moreVisibility);
    }

    private void fill(LinearLayout list, List<RuleTip> tips, String language) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (RuleTip tip : tips) {
            View item = inflater.inflate(R.layout.item_tip, list, false);
            ((TextView) item.findViewById(R.id.tip_text)).setText(tip.text(language));
            ((TextView) item.findViewById(R.id.tip_meta)).setText(meta(tip));
            list.addView(item);
        }
    }

    /** Small label under each tip: "For you · Asthma", or the weather it's for. */
    private String meta(RuleTip tip) {
        if (tip.getSensitivity() != null) {
            return getString(R.string.tips_meta_for_you, WeatherStateLabels.sensitivity(this, tip.getSensitivity()));
        }
        return WeatherStateLabels.state(this, tip.getState());
    }

    // ---------- Preview weather (debug builds only) ----------

    private void setUpPreview() {
        ChipGroup previewChips = findViewById(R.id.tips_preview_chips);
        List<String> current = CurrentWeatherStates.get().getValue();
        for (String state : Schema.WEATHER_STATES) {
            if (Schema.STATE_NORMAL.equals(state)) continue; // none ticked = normal
            Chip chip = new Chip(this);
            chip.setText(WeatherStateLabels.state(this, state));
            chip.setTag(state);
            chip.setCheckable(true);
            chip.setChecked(current != null && current.contains(state));
            chip.setOnCheckedChangeListener((button, checked) -> applyPreview(previewChips));
            previewChips.addView(chip);
        }
    }

    private void applyPreview(ChipGroup previewChips) {
        List<String> states = new ArrayList<>();
        for (int i = 0; i < previewChips.getChildCount(); i++) {
            Chip chip = (Chip) previewChips.getChildAt(i);
            if (chip.isChecked()) states.add((String) chip.getTag());
        }
        CurrentWeatherStates.preview(states);
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}