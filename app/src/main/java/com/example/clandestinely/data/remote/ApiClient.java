package com.example.clandestinely.data.remote;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static final String FORECAST_BASE_URL = "https://api.open-meteo.com/";
    private static final String AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/";

    private static OpenMeteoApi weatherApi;
    private static OpenMeteoAirQualityApi airQualityApi;

    public static synchronized OpenMeteoApi getWeatherApi() {
        if (weatherApi == null) {
            weatherApi = build(FORECAST_BASE_URL).create(OpenMeteoApi.class);
        }
        return weatherApi;
    }

    public static synchronized OpenMeteoAirQualityApi getHazeApi() {
        if (airQualityApi == null) {
            airQualityApi = build(AIR_QUALITY_BASE_URL).create(OpenMeteoAirQualityApi.class);
        }
        return airQualityApi;
    }

    private static Retrofit build(String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
