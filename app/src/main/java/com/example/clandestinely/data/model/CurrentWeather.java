package com.example.clandestinely.data.model;

public class CurrentWeather {

    public final double temperature;
    public final double apparent_temperature;
    public final int humidity;
    public final int precipitation_probability;
    public final int usAqi;
    public final int weather_code;
    public final long updatedAt;

    public CurrentWeather(double temperature, double apparent_temperature, int humidity, int precipitation_probability, int usAqi, int weatherCode, long updatedAt) {
        this.temperature = temperature;
        this.apparent_temperature = apparent_temperature;
        this.humidity = humidity;
        this.precipitation_probability = precipitation_probability;
        this.usAqi = usAqi;
        weather_code = weatherCode;
        this.updatedAt = updatedAt;
    }
}
