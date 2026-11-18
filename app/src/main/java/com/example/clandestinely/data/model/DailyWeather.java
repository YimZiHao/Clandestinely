package com.example.clandestinely.data.model;

/** One day of the daily forecast row (M1). */
public class DailyWeather {

    public final String date;                       // "2026-11-12", local date
    public final double temperatureMax;
    public final double temperatureMin;
    public final double apparentTemperatureMax;
    public final int precipitationProbabilityMax;
    public final int weather_code;
    public final double temperatureMean;            // shown as the main number
    public final double apparentTemperatureMean;    // shown as "Feels"
    public final int humidityMean;                  // shown in the rain card when tapped
    public final int usAqiMax;                      // worst hour of the day, or AQI_UNAVAILABLE
    public final boolean isToday;

    public DailyWeather(String date, double temperatureMax, double temperatureMin,
                        double apparentTemperatureMax, int precipitationProbabilityMax,
                        int weather_code, double temperatureMean, double apparentTemperatureMean,
                        int humidityMean, int usAqiMax, boolean isToday) {
        this.date = date;
        this.temperatureMax = temperatureMax;
        this.temperatureMin = temperatureMin;
        this.apparentTemperatureMax = apparentTemperatureMax;
        this.precipitationProbabilityMax = precipitationProbabilityMax;
        this.weather_code = weather_code;
        this.temperatureMean = temperatureMean;
        this.apparentTemperatureMean = apparentTemperatureMean;
        this.humidityMean = humidityMean;
        this.usAqiMax = usAqiMax;
        this.isToday = isToday;
    }
}
