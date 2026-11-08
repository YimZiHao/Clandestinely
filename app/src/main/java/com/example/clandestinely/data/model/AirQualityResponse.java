package com.example.clandestinely.data.model;

public class AirQualityResponse {

    public Current current;

    public static class Current{
        public String time;
        public int us_aqi;
    }
}
