package com.example.clandestinely.data.remote;

import com.example.clandestinely.data.model.AirQualityResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    Call<AirQualityResponse> getAirQuality(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current") String currentFields,
            @Query("hourly") String hourlyFields,       // "us_aqi" for the forecast rows
            @Query("timezone") String timezone,
            @Query("forecast_days") int forecastDays    // AQI has values for ~5 days only
    );

}
