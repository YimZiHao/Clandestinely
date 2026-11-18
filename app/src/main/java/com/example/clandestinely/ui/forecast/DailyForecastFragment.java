package com.example.clandestinely.ui.forecast;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.DailyWeather;
import com.example.clandestinely.ui.home.WeatherViewModel;

/** M1 daily forecast: 7 days. The "Hourly Forecast" button goes back to ForecastFragment. */
public class DailyForecastFragment extends Fragment {

    // The day tapped in the row, or null = today (rain card shows current weather).
    @Nullable
    private DailyWeather selectedDay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.daily_forecast_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView dayList = view.findViewById(R.id.RVForecastDays);
        // Tapping a column shows that day in the AQI and rain cards.
        ForecastDayAdapter adapter = new ForecastDayAdapter(day -> {
            selectedDay = day;
            ForecastFragment.showAqiAndRain(view, ForecastDayAdapter.asCurrent(day));
        });
        dayList.setAdapter(adapter);

        WeatherViewModel viewModel = new ViewModelProvider(this).get(WeatherViewModel.class);
        viewModel.getDaily().observe(getViewLifecycleOwner(), days -> {
            if (days != null) {
                adapter.submit(days);                   // selection resets to today
                selectedDay = null;
            }
        });
        viewModel.getWeather().observe(getViewLifecycleOwner(), w -> {
            ForecastFragment.showAqiAndRain(view, w);
            // Keep showing the tapped day if the weather refreshes after a tap.
            if (selectedDay != null) {
                ForecastFragment.showAqiAndRain(view, ForecastDayAdapter.asCurrent(selectedDay));
            }
        });

        view.findViewById(R.id.buttonHourly).setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ForecastFragment())
                        .commit());
    }
}
