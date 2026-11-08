package com.example.clandestinely.data.remote;

import com.example.clandestinely.data.model.ForecastResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoApi {

    @GET("v1/forecast")
    Call<ForecastResponse> getForecast(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current") String currentFields,
            @Query("hourly") String hourlyFields,
            @Query("timezone") String timezone,        // "Asia/Kuala_Lumpur"
            @Query("forecast_hours") int forecastHours // 24
    );
}
