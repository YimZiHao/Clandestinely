# Weather Rules

| State        | Input field(s)                | Rule                                                    | Source                                          | Reason                                                                                                                                                                                                  |
|--------------|-------------------------------|---------------------------------------------------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Rain         | precipitation_probability (%) | ≥ 70%| Open&#8209;Meteo&nbsp;Forecast&nbsp;API| At 70% or above, at least 7 in 10 of Open-Meteo's ensemble model runs forecast more than 0.1 mm of rain in that hour.|
| Thunderstorm | weather_code| ≥ 95| Open&#8209;Meteo&nbsp;Forecast&nbsp;API| More than or equals to 95 weather_code indicates a thunderstorm.|
| Heat         | apparent_temperature(°C)| ≥ 38°C| Open&#8209;Meteo&nbsp;Forecast&nbsp;API| Apparent temperature is not actual temperature. 38°C is higher than 90% of apparent_temperature in 2025.|
| Haze         | us_aqi| ≥ 101: Unhealthy for Sensitive Groups; ≥ 151: Unhealthy | Open&#8209;Meteo&nbsp;Air&nbsp;Quality&nbsp;API | At 101 or above, air is unhealthy for sensitive groups (people with asthma or heart/lung conditions, children and older adults). At 151 or above, it is unhealthy for everyone (US EPA AQI categories). |
| Normal       | - | -| - | Normal is the default state.|
