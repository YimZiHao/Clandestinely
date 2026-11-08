package com.example.clandestinely.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.clandestinely.data.local.WeatherCache;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.remote.ApiClient;
import com.example.clandestinely.data.repository.WeatherRepository;

public class WeatherViewModel extends AndroidViewModel {

    private final WeatherRepository weatherRepository;

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        weatherRepository = new WeatherRepository(ApiClient.getWeatherApi(),
                ApiClient.getHazeApi(), new WeatherCache(application));
        weatherRepository.refresh();
    }

    public LiveData<CurrentWeather> getWeather() {
        return weatherRepository.getWeather();
    }

    public void refresh() {
        weatherRepository.refresh();
    }
}
