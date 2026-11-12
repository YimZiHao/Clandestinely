package com.example.clandestinely.data.model;

/** One hour of the forecast row (M1). */
public class HourlyWeather {

    public final String time;               // "2026-11-12T20:00", local time
    public final double temperature;
    public final double apparent_temperature;
    public final int precipitation_probability;
    public final int weather_code;
    public final int humidity;
    public final int usAqi;                         // WeatherRepository.AQI_UNAVAILABLE if unknown
    public final boolean isCurrentHour;

    public HourlyWeather(String time, double temperature, double apparent_temperature,
                         int precipitation_probability, int weather_code, int humidity,
                         int usAqi, boolean isCurrentHour) {
        this.time = time;
        this.temperature = temperature;
        this.apparent_temperature = apparent_temperature;
        this.precipitation_probability = precipitation_probability;
        this.weather_code = weather_code;
        this.humidity = humidity;
        this.usAqi = usAqi;
        this.isCurrentHour = isCurrentHour;
    }
}
