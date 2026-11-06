package com.example.clandestinely;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.clandestinely.R;
import com.google.android.material.color.MaterialColors;

// Temporary screen. Replace with the real fragments once teammates finish them.
public class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "title";

    public static PlaceholderFragment newInstance(String title) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        TextView title = new TextView(requireContext());
        title.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        title.setGravity(Gravity.CENTER);
        title.setTextAppearance(R.style.TextAppearance_Cuaca_HeadlineLarge);
        title.setTextColor(MaterialColors.getColor(
                title, com.google.android.material.R.attr.colorOnBackground));

        String text = getArguments() != null ? getArguments().getString(ARG_TITLE) : "";
        title.setText(text);
        return title;
    }
}