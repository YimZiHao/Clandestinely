package com.example.clandestinely.ui.tips;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.clandestinely.R;

/**
 * The Home card (M3): the current weather and the top tip. Tap it to open the full Tips screen.
 *
 * To put it on a screen, add this to that screen's layout:
 *
 * <androidx.fragment.app.FragmentContainerView
 *     android:id="@+id/tip_card_container"
 *     android:name="com.example.clandestinely.ui.tips.TipCardFragment"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 */
public class TipCardFragment extends Fragment {

    private TipsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tip_card, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView stateText = view.findViewById(R.id.tip_card_state);
        TextView tipText = view.findViewById(R.id.tip_card_text);

        view.findViewById(R.id.tip_card).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TipsActivity.class)));

        viewModel = new ViewModelProvider(this).get(TipsViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), s -> {
            stateText.setText(WeatherStateLabels.join(requireContext(), s.states));
            tipText.setText(s.topTips.isEmpty()
                    ? getString(R.string.tips_none)
                    : s.topTips.get(0).text(s.language));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refresh();
    }
}