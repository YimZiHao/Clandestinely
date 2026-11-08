package com.example.clandestinely.data.local;

import com.example.clandestinely.data.model.CurrentWeather;

public class WeatherCache {

    public CurrentWeather currentWeather;
    public void save(CurrentWeather currentWeather) {
        this.currentWeather = currentWeather;
    }

    public CurrentWeather load() {
        return currentWeather;
    }
}
