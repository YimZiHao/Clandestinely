package com.example.clandestinely.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.clandestinely.data.local.WeatherCache;
import com.example.clandestinely.data.model.AirQualityResponse;
import com.example.clandestinely.data.model.CurrentWeather;
import com.example.clandestinely.data.model.ForecastResponse;
import com.example.clandestinely.data.remote.OpenMeteoAirQualityApi;
import com.example.clandestinely.data.remote.OpenMeteoApi;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherRepository {

    private final OpenMeteoApi weatherApi;
    private final OpenMeteoAirQualityApi hazeApi;
    private static final double LAT = 3.1209;
    private static final double LON = 101.6538;
    private static final String TIMEZONE = "Asia/Kuala_Lumpur";
    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m";
    private static final String AQI_FIELDS = "us_aqi";
    public static final int AQI_UNAVAILABLE = -1;
    private static final String HOURLY_FIELDS =
            "precipitation_probability";
    private static final int FORECAST_HOURS = 24;
    private final WeatherCache cache;
    private final MutableLiveData<CurrentWeather> weather = new MutableLiveData<>();

    public WeatherRepository(OpenMeteoApi weatherApi, OpenMeteoAirQualityApi hazeApi, WeatherCache cache) {
        this.weatherApi = weatherApi;
        this.hazeApi = hazeApi;
        this.cache = cache;
    }

    public LiveData<CurrentWeather> getWeather() {
        return weather;
    }

    /***
     * Calls both OpenMeteoApi and OpenMeteoAirQualityApi
     */
    public void refresh() {
        CurrentWeather currentWeather = cache.load();
        if (currentWeather != null) {
            weather.setValue(currentWeather); //NFR1 Instant Display
        }
        // call Open Meteo API
        weatherApi.getForecast(LAT, LON, CURRENT_FIELDS, HOURLY_FIELDS, TIMEZONE, FORECAST_HOURS)
                .enqueue(new Callback<ForecastResponse>() {
                    @Override
                    public void onResponse(Call<ForecastResponse> call,
                                           Response<ForecastResponse> response) {
                        // runs when the server replies
                        ForecastResponse forecast = response.body();

                        // 400 server error
                        if (!response.isSuccessful() || forecast == null
                                || forecast.current == null || forecast.hourly == null) {
                            Log.e("WeatherRepository", "Bad response: HTTP " + response.code());
                            return; // keep showing cached weather
                        }

                        // 200 OK
                        double temperature = forecast.current.temperature_2m;
                        double apparentTemperature = forecast.current.apparent_temperature;
                        int humidity = forecast.current.relative_humidity_2m;
                        List<String> time = forecast.hourly.time;
                        List<Integer> precipitationProbability = forecast.hourly.precipitation_probability;
                        String currentHour = forecast.current.time.substring(0, 13);

                        int rainChance = 0;                       // fallback if no match
                        for (int i = 0; i < time.size(); i++) {
                            if (time.get(i).substring(0, 13).equals(currentHour)) {
                                rainChance = precipitationProbability.get(i);
                                break;                            // found it, stop looping
                            }
                        }

                        // call Open Meteo Air Quality API
                        int finalRainChance = rainChance;
                        hazeApi.getAirQuality(LAT, LON, AQI_FIELDS, TIMEZONE)
                                .enqueue(new Callback<AirQualityResponse>() {
                                    @Override
                                    public void onResponse(Call<AirQualityResponse> call, Response<AirQualityResponse> response) {

                                        // runs when the server replies
                                         AirQualityResponse airQualityResponse = response.body();

                                        // 400 server error
                                        if (!response.isSuccessful() || airQualityResponse == null
                                                || airQualityResponse.current == null) {
                                            Log.e("WeatherRepository", "Bad AQI response: HTTP " + response.code());
                                            // weather is still good, so show it without fresh AQI
                                            publish(temperature, apparentTemperature, humidity, finalRainChance, fallbackAqi());
                                            return;
                                        }

                                        // 200 OK
                                        int usAqi = airQualityResponse.current.us_aqi;
                                        publish(temperature, apparentTemperature, humidity, finalRainChance, usAqi);
                                    }

                                    @Override
                                    public void onFailure(Call<AirQualityResponse> call, Throwable t) {
                                        // runs when there's no network / request failed
                                        Log.e("WeatherRepository", "Open Meteo Air Quality API request failed. Using fallback AQI.", t);
                                        publish(temperature, apparentTemperature, humidity, finalRainChance, fallbackAqi());
                                    }
                                });


                    }

                    @Override
                    public void onFailure(Call<ForecastResponse> call, Throwable t) {
                        // runs when there's no network / request failed
                        Log.e("WeatherRepository", "Open Meteo API Forecast request failed. Keeping cached weather.", t);
                    }
                });
    }

    /***
     * Builds the CurrentWeather, saves it to the cache and pushes it to the UI.
     *
     * @param temperature
     * @param apparentTemperature
     * @param humidity
     * @param rainChance
     * @param usAqi
     */
    private void publish(double temperature, double apparentTemperature, int humidity,
                         int rainChance, int usAqi) {
        CurrentWeather current = new CurrentWeather(temperature, apparentTemperature, humidity,
                rainChance, usAqi, System.currentTimeMillis());
        cache.save(current);
        weather.setValue(current);
    }

    // AQI to use when the air quality call fails: last cached value, or -1 = unavailable.
    private int fallbackAqi() {
        CurrentWeather cached = cache.load();
        return cached != null ? cached.usAqi : AQI_UNAVAILABLE;
    }
}
