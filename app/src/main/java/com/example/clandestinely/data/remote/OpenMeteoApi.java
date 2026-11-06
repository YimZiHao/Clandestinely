package com.example.clandestinely.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoApi {

    @GET("v1/forecast")
    Call<ForecastResponse> getForecast(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current") String currentFields
    );
}
