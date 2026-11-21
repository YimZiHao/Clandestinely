package com.example.clandestinely.ui.home;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.repository.WeatherRepository;
import com.example.clandestinely.data.weather.WeatherClassifier;

import java.util.Date;

public class HomeFragment extends Fragment {

    private TextView textTemperature, textFeelsLike, textHumidity, textRainChance, textAqi, textLastUpdated, textSummary;
    private String pendingPhoneNumber;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    EmergencyInfo.startCallIntent(this, pendingPhoneNumber);
                } else if (pendingPhoneNumber != null && isAdded()) {
                    EmergencyInfo.startDialIntent(this, pendingPhoneNumber);
                } else if (isAdded()) {
                    Toast.makeText(requireContext(), "Permission DENIED", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Find the TextViews
        textTemperature = view.findViewById(R.id.TVTemperature);
        textFeelsLike   = view.findViewById(R.id.TVFeelsLike);
        textHumidity    = view.findViewById(R.id.TVHumidity);
        textRainChance  = view.findViewById(R.id.TVRainChance);
        textAqi         = view.findViewById(R.id.TVAqi);
        textLastUpdated = view.findViewById(R.id.TVLastUpdated);
        textSummary = view.findViewById(R.id.TVSummary);

        // 2. Get the ViewModel (survives screen rotation)
        WeatherViewModel viewModel = new ViewModelProvider(this).get(WeatherViewModel.class);

        // 3. Update the screen whenever new weather arrives
        viewModel.getWeather().observe(getViewLifecycleOwner(), this::showWeather);

        // 3. Delegate Emergency & Calling Features to EmergencyInfo
        EmergencyInfo.setupEmergencyFeatures(this, view, phoneNumber -> {
            pendingPhoneNumber = phoneNumber;
            EmergencyInfo.makePhoneCall(this, requestPermissionLauncher, phoneNumber);
        });
    }

    private void showWeather(CurrentWeather w) {
        if (w == null) return;

        textTemperature.setText(getString(R.string.home_temperature, w.temperature));
        textFeelsLike.setText(getString(R.string.home_feels_like, w.apparent_temperature));
        textHumidity.setText(getString(R.string.home_humidity, w.humidity));
        textRainChance.setText(getString(R.string.home_rain_chance, w.precipitation_probability));

        if (w.usAqi == WeatherRepository.AQI_UNAVAILABLE) {
            textAqi.setText(R.string.home_aqi_unavailable);
        } else {
            textAqi.setText(getString(R.string.home_aqi, w.usAqi));
        }

        String time = DateFormat.getTimeFormat(requireContext()).format(new Date(w.updatedAt));
        textLastUpdated.setText(getString(R.string.home_last_updated, time));

        String state = WeatherClassifier.mostSevere(WeatherClassifier.classify(w));
        textSummary.setText(summaryFor(state));
    }

    @StringRes
    private static int summaryFor(String state) {
        switch (state) {
            case Schema.STATE_THUNDERSTORM:   return R.string.summary_thunderstorm;
            case Schema.STATE_HAZE_UNHEALTHY: return R.string.summary_haze_unhealthy;
            case Schema.STATE_RAIN:           return R.string.summary_rain;
            case Schema.STATE_HEAT:           return R.string.summary_heat;
            case Schema.STATE_HAZE_SENSITIVE: return R.string.summary_haze_sensitive;
            default:                          return R.string.summary_normal;
        }
    }
}
