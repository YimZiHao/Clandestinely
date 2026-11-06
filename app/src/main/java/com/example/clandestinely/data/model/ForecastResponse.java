package com.example.clandestinely.data.model;

public class ForecastResponse {

    public Current current;

    public static class Current {
        public int precipitation_probability;
        public double apparent_temperature;
        
    }
}
