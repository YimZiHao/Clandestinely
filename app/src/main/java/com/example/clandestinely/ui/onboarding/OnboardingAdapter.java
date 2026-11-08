package com.example.clandestinely.ui.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clandestinely.R;

/** Fills the 3 onboarding slides (icon, title, description) for the ViewPager2. */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideHolder> {

    // One row per slide: icon, title, description.
    private static final int[][] SLIDES = {
            {R.drawable.ic_onb_weather, R.string.onb_slide1_title, R.string.onb_slide1_body},
            {R.drawable.ic_onb_tips, R.string.onb_slide2_title, R.string.onb_slide2_body},
            {R.drawable.ic_onb_route, R.string.onb_slide3_title, R.string.onb_slide3_body},
    };

    @NonNull
    @Override
    public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_slide, parent, false);
        return new SlideHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideHolder holder, int position) {
        int[] slide = SLIDES[position];
        holder.icon.setImageResource(slide[0]);
        holder.title.setText(slide[1]);
        holder.body.setText(slide[2]);
    }

    @Override
    public int getItemCount() {
        return SLIDES.length;
    }

    static class SlideHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView body;

        SlideHolder(@NonNull View view) {
            super(view);
            icon = view.findViewById(R.id.slide_icon);
            title = view.findViewById(R.id.slide_title);
            body = view.findViewById(R.id.slide_body);
        }
    }
}
