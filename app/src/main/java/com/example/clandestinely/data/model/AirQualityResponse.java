package com.example.clandestinely.data.model;

import java.util.List;

public class AirQualityResponse {

    public Current current;
    public Hourly hourly;

    public static class Current{
        public String time;
        public int us_aqi;
    }

    public static class Hourly {
        public List<String> time;                          // "2026-11-12T15:00"
        public List<Integer> us_aqi;                       // null where there's no forecast yet
    }
}
