package com.example.clandestinely.ui.forecast;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.DailyWeather;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.weather.WeatherClassifier;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The daily forecast row: one item_forecast_column per day. */
public class ForecastDayAdapter extends RecyclerView.Adapter<ForecastDayAdapter.DayViewHolder> {

    /** Called when the user taps a column. */
    public interface OnDaySelected {
        void onDaySelected(DailyWeather day);
    }

    private final List<DailyWeather> days = new ArrayList<>();
    private final OnDaySelected listener;
    private int selectedPosition = 0;                   // the bold column

    public ForecastDayAdapter(OnDaySelected listener) {
        this.listener = listener;
    }

    /** New data: the selection goes back to today. */
    public void submit(List<DailyWeather> newDays) {
        days.clear();
        days.addAll(newDays);
        selectedPosition = 0;
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).isToday) {
                selectedPosition = i;
                break;
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_forecast_column, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        DailyWeather day = days.get(position);
        Context context = holder.itemView.getContext();
        Date date = parseDate(day.date);

        // Same column layout as the hourly row: day name where the time goes, then the date.
        holder.day.setText(date != null ? format(date, "EEE") : day.date);
        holder.date.setText(date != null ? format(date, "d MMM") : "");
        // Daily averages: mean temperature, and mean feels-like underneath.
        holder.temp.setText(context.getString(R.string.forecast_temperature, day.temperatureMean));
        holder.feels.setText(context.getString(R.string.forecast_feels_like, day.apparentTemperatureMean));
        holder.emoji.setText(emojiFor(day));

        // The selected column is bold, the rest normal.
        int style = position == selectedPosition ? Typeface.BOLD : Typeface.NORMAL;
        holder.day.setTypeface(null, style);
        holder.temp.setTypeface(null, style);
        holder.feels.setTypeface(null, style);

        holder.itemView.setOnClickListener(v -> {
            int tapped = holder.getBindingAdapterPosition();
            if (tapped == RecyclerView.NO_POSITION || tapped == selectedPosition) return;
            int previous = selectedPosition;
            selectedPosition = tapped;
            notifyItemChanged(previous);                // un-bold the old column
            notifyItemChanged(tapped);                  // bold the new one
            listener.onDaySelected(days.get(tapped));
        });
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        final TextView day, date, temp, feels, emoji;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            day = itemView.findViewById(R.id.TVColumnTime);
            date = itemView.findViewById(R.id.TVColumnDate);
            temp = itemView.findViewById(R.id.TVColumnTemperature);
            feels = itemView.findViewById(R.id.TVColumnFeelsLike);
            emoji = itemView.findViewById(R.id.TVColumnEmoji);
        }
    }

    /**
     * One day as a CurrentWeather, so WeatherClassifier and the rain card can use it:
     * the day's highest rain chance, feels-like and AQI, and its average humidity.
     */
    static CurrentWeather asCurrent(DailyWeather day) {
        return new CurrentWeather(day.temperatureMax, day.apparentTemperatureMax, day.humidityMean,
                day.precipitationProbabilityMax, day.usAqiMax, day.weather_code, 0);
    }

    /** "2026-11-12" → Date, or null if it can't be parsed. */
    private static Date parseDate(String isoDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate);
        } catch (ParseException e) {
            return null;
        }
    }

    private static String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }

    /**
     * Same rules as the Home summary (WeatherClassifier), using the day's highest feels-like
     * highest rain chance and worst-hour AQI.
     */
    @StringRes
    private static int emojiFor(DailyWeather day) {
        String state = WeatherClassifier.mostSevere(WeatherClassifier.classify(asCurrent(day)));
        switch (state) {
            case Schema.STATE_THUNDERSTORM:   return R.string.emoji_thunderstorm;
            case Schema.STATE_HAZE_UNHEALTHY: return R.string.emoji_haze_unhealthy;
            case Schema.STATE_RAIN:           return R.string.emoji_rain;
            case Schema.STATE_HEAT:           return R.string.emoji_heat;
            case Schema.STATE_HAZE_SENSITIVE: return R.string.emoji_haze_sensitive;
            default:                          return R.string.emoji_normal;
        }
    }
}
