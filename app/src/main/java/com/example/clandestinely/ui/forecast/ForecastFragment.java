package com.example.clandestinely.ui.forecast;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.HourlyWeather;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.repository.WeatherRepository;
import com.example.clandestinely.data.weather.WeatherClassifier;
import com.example.clandestinely.ui.home.WeatherViewModel;

import java.util.List;

public class ForecastFragment extends Fragment {

    // The hour tapped in the row, or null = the current hour (rain card shows current weather).
    @Nullable
    private HourlyWeather selectedHour;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.forecast_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView hourList = view.findViewById(R.id.RVForecastHours);
        // Tapping a column shows that hour in the AQI and rain cards.
        ForecastHourAdapter adapter = new ForecastHourAdapter(hour -> {
            selectedHour = hour;
            showAqiAndRain(view, ForecastHourAdapter.asCurrent(hour));
        });
        hourList.setAdapter(adapter);

        WeatherViewModel viewModel = new ViewModelProvider(this).get(WeatherViewModel.class);
        viewModel.getHourly().observe(getViewLifecycleOwner(), hours -> {
            if (hours != null) {
                adapter.submit(hours);                  // selection resets to the current hour
                selectedHour = null;
            }
        });
        viewModel.getWeather().observe(getViewLifecycleOwner(), w -> {
            showAqiAndRain(view, w);
            // Keep showing the tapped hour if the weather refreshes after a tap.
            if (selectedHour != null) showAqiAndRain(view, ForecastHourAdapter.asCurrent(selectedHour));
        });

        view.findViewById(R.id.buttonDaily).setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new DailyForecastFragment())
                        .commit());
    }

    /**
     Middle card = AQI, bottom card = rain chance. Emojis follow WeatherClassifier's states.
     */
    // Static so DailyForecastFragment can use it too; its layout has the same card ids.
    static void showAqiAndRain(View root, CurrentWeather w) {
        if (w == null) return;
        List<String> states = WeatherClassifier.classify(w);
        Context context = root.getContext();
        TextView aqiEmoji = root.findViewById(R.id.TVAqiEmoji);
        TextView aqi = root.findViewById(R.id.TVForecastAqi);
        TextView aqiSummary = root.findViewById(R.id.TVAqiSummary);

        if (w.usAqi == WeatherRepository.AQI_UNAVAILABLE) {
            aqi.setText(R.string.home_aqi_unavailable);
            aqiEmoji.setText("");
            aqiSummary.setText("");
        } else {
            aqi.setText(context.getString(R.string.home_aqi, w.usAqi));
            if (states.contains(Schema.STATE_HAZE_UNHEALTHY)) {
                aqiEmoji.setText(R.string.emoji_haze_unhealthy);
                aqiSummary.setText(R.string.aqicard_haze_unhealthy);
            } else if (states.contains(Schema.STATE_HAZE_SENSITIVE)) {
                aqiEmoji.setText(R.string.emoji_haze_sensitive);
                aqiSummary.setText(R.string.aqicard_haze_sensitive);
            } else {
                aqiEmoji.setText(R.string.emoji_air_good);   // below AQI_SENSITIVE
                aqiSummary.setText(R.string.aqicard_haze_healthy);
            }
        }

        showRain(root, w);
    }

    /** Bottom card only: rain chance, humidity and the rain/storm emoji. */
    static void showRain(View root, CurrentWeather w) {
        List<String> states = WeatherClassifier.classify(w);
        Context context = root.getContext();
        TextView rainEmoji = root.findViewById(R.id.TVRainEmoji);
        TextView rain = root.findViewById(R.id.TVForecastRain);
        TextView humidity = root.findViewById(R.id.TVForecastHumidity);

        rain.setText(context.getString(R.string.home_rain_chance, w.precipitation_probability));
        humidity.setText(context.getString(R.string.home_humidity, w.humidity));
        if (states.contains(Schema.STATE_THUNDERSTORM)) {
            rainEmoji.setText(R.string.emoji_thunderstorm);
        } else if (states.contains(Schema.STATE_RAIN)) {
            rainEmoji.setText(R.string.emoji_rain);
        } else {
            rainEmoji.setText(R.string.emoji_normal);
        }
    }

}
