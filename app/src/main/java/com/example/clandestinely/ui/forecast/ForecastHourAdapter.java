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
import com.example.clandestinely.data.model.HourlyWeather;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.weather.WeatherClassifier;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The hourly forecast row: one item_forecast_column per hour. */
public class ForecastHourAdapter extends RecyclerView.Adapter<ForecastHourAdapter.HourViewHolder> {

    /** Called when the user taps a column. */
    public interface OnHourSelected {
        void onHourSelected(HourlyWeather hour);
    }

    private final List<HourlyWeather> hours = new ArrayList<>();
    private final OnHourSelected listener;
    private int selectedPosition = 0;                   // the bold column

    public ForecastHourAdapter(OnHourSelected listener) {
        this.listener = listener;
    }

    /** New data: the selection goes back to the current hour. */
    public void submit(List<HourlyWeather> newHours) {
        hours.clear();
        hours.addAll(newHours);
        selectedPosition = 0;
        for (int i = 0; i < hours.size(); i++) {
            if (hours.get(i).isCurrentHour) {
                selectedPosition = i;
                break;
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HourViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_forecast_column, parent, false);
        return new HourViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HourViewHolder holder, int position) {
        HourlyWeather hour = hours.get(position);
        Context context = holder.itemView.getContext();

        holder.time.setText(context.getString(R.string.forecast_hour, formatHour(hour.time)));
        holder.date.setText(formatDate(hour.time));
        holder.temp.setText(context.getString(R.string.forecast_temperature, hour.temperature));
        holder.feels.setText(context.getString(R.string.forecast_feels_like, hour.apparent_temperature));
        holder.emoji.setText(emojiFor(hour));

        // The selected column is bold, the rest normal.
        int style = position == selectedPosition ? Typeface.BOLD : Typeface.NORMAL;
        holder.time.setTypeface(null, style);
        holder.temp.setTypeface(null, style);
        holder.feels.setTypeface(null, style);

        holder.itemView.setOnClickListener(v -> {
            int tapped = holder.getBindingAdapterPosition();
            if (tapped == RecyclerView.NO_POSITION || tapped == selectedPosition) return;
            int previous = selectedPosition;
            selectedPosition = tapped;
            notifyItemChanged(previous);                // un-bold the old column
            notifyItemChanged(tapped);                  // bold the new one
            listener.onHourSelected(hours.get(tapped));
        });
    }

    @Override
    public int getItemCount() {
        return hours.size();
    }

    static class HourViewHolder extends RecyclerView.ViewHolder {
        final TextView time, date, temp, feels, emoji;

        HourViewHolder(@NonNull View itemView) {
            super(itemView);
            time = itemView.findViewById(R.id.TVColumnTime);
            date = itemView.findViewById(R.id.TVColumnDate);
            temp = itemView.findViewById(R.id.TVColumnTemperature);
            feels = itemView.findViewById(R.id.TVColumnFeelsLike);
            emoji = itemView.findViewById(R.id.TVColumnEmoji);
        }
    }

    /**
     * "2026-11-12T15:00" → "3 PM". Falls back to the raw text if it can't be parsed.
     */
    private static String formatHour(String isoTime) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("h a", Locale.getDefault());
            return out.format(in.parse(isoTime));
        } catch (ParseException e) {
            return isoTime;
        }
    }

    /** One hour as a CurrentWeather, so WeatherClassifier and the AQI/rain cards can use it. */
    static CurrentWeather asCurrent(HourlyWeather hour) {
        return new CurrentWeather(hour.temperature, hour.apparent_temperature, hour.humidity,
                hour.precipitation_probability, hour.usAqi, hour.weather_code, 0);
    }

    /**
     * "2026-11-12T15:00" → "12 Nov". Falls back to the date part if it can't be parsed.
     */
    private static String formatDate(String isoTime) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("d MMM", Locale.getDefault());
            return out.format(in.parse(isoTime));
        } catch (ParseException e) {
            return isoTime.substring(0, Math.min(10, isoTime.length()));
        }
    }

    /**
     * Same rules as the Home summary (WeatherClassifier), including haze from the hour's AQI.
     */
    @StringRes
    private static int emojiFor(HourlyWeather hour) {
        String state = WeatherClassifier.mostSevere(WeatherClassifier.classify(asCurrent(hour)));
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
