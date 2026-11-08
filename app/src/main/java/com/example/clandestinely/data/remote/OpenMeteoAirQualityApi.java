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
            @Query("timezone") String timezone
    );

}
