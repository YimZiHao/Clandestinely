package com.example.clandestinely.data.repository;

import androidx.lifecycle.MutableLiveData;

public class WeatherRepository {

    private final OpenMeteoApi api;
    private final WeatherCache cahce;
    private final MutableLiveData<CurrentWeather> weather = new MutableLiveData<>();

    public liveData<CurrentWeather> getWeather() {

    }
}
