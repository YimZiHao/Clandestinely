package com.example.clandestinely.data.model;

import java.util.List;

public class ForecastResponse {

    public Current current;
    public Hourly hourly;

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
    }
}
