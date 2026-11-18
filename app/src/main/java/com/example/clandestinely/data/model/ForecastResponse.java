package com.example.clandestinely.data.model;

import java.util.List;

public class ForecastResponse {

    public Current current;
    public Hourly hourly;
    public Daily daily;

    public static class Current {
        public int precipitation_probability;
        public double temperature_2m;
        public double apparent_temperature;
        public int weather_code;
        public int relative_humidity_2m;
        public String time;
    }

    public static class Hourly {
        public List<String> time;
        public List<Integer> precipitation_probability;
        public List<Double> temperature_2m;
        public List<Double> apparent_temperature;
        public List<Integer> weather_code;
        public List<Integer> relative_humidity_2m;
    }

    public static class Daily {
        public List<String> time;                          // "2026-11-12"
        public List<Integer> weather_code;
        public List<Double> temperature_2m_max;
        public List<Double> temperature_2m_min;
        public List<Double> apparent_temperature_max;
        public List<Integer> precipitation_probability_max;
        public List<Double> temperature_2m_mean;
        public List<Double> apparent_temperature_mean;
        public List<Integer> relative_humidity_2m_mean;
    }
}
