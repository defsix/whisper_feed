/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.manager.glance

import android.util.Log
import com.saulhdev.feeder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A place, and the current conditions there.
 *
 * @param sunsetLocal sunset today as "HH:mm" in the place's own local time, or
 *   null if the response did not carry one.
 */
data class GlanceWeather(
    val place: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val sunriseLocal: String?,
    val sunsetLocal: String?,
    val fetchedAt: Long,
)

data class GlancePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Weather and sunrise/sunset, from Open-Meteo.
 *
 * Open-Meteo is used because it needs no API key and no account, does not
 * require Play Services, and states that it does not track users — which is
 * what makes it compatible with the project's "no analytics, no telemetry" rule.
 * Its forecast endpoint returns the current conditions *and* today's sunrise and
 * sunset in one response, so both chips cost a single request.
 *
 * Location comes from a place the user searched for, not from the device: no
 * runtime permission is requested and no coordinate leaves the app except the
 * one the user chose themselves. The coordinates are rounded before being sent,
 * which is plenty for weather at city scale and avoids handing a third party a
 * precise position.
 */
class WeatherRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Looks up places by name. Returns an empty list rather than throwing. */
    suspend fun searchPlaces(query: String): List<GlancePlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}" +
                    "&count=8&format=json"
            val body = get(url) ?: return@withContext emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).map { i ->
                val o = results.getJSONObject(i)
                val parts = listOfNotNull(
                    o.optString("name").takeIf { it.isNotBlank() },
                    o.optString("admin1").takeIf { it.isNotBlank() },
                    o.optString("country").takeIf { it.isNotBlank() },
                )
                GlancePlace(
                    name = parts.joinToString(", "),
                    latitude = o.optDouble("latitude"),
                    longitude = o.optDouble("longitude"),
                )
            }
        }.getOrElse {
            Log.w(TAG, "Place search failed", it)
            emptyList()
        }
    }

    /** Current conditions plus today's sunrise and sunset. Null on any failure. */
    suspend fun fetch(place: GlancePlace): GlanceWeather? = withContext(Dispatchers.IO) {
        runCatching {
            // Two decimal places is about a kilometre — enough for city weather,
            // and deliberately not a precise position.
            val lat = String.format(java.util.Locale.US, "%.2f", place.latitude)
            val lon = String.format(java.util.Locale.US, "%.2f", place.longitude)
            val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=sunrise,sunset" +
                    "&timezone=auto&forecast_days=1"
            val body = get(url) ?: return@withContext null
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val daily = root.optJSONObject("daily")
            GlanceWeather(
                place = place.name,
                temperatureC = current.optDouble("temperature_2m"),
                weatherCode = current.optInt("weather_code", -1),
                // "2026-09-07T06:41" -> "06:41"
                sunriseLocal = daily?.optJSONArray("sunrise")?.optString(0)?.substringAfter('T'),
                sunsetLocal = daily?.optJSONArray("sunset")?.optString(0)?.substringAfter('T'),
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrElse {
            Log.w(TAG, "Weather fetch failed", it)
            null
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Whisper RSS reader")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for $url")
                return null
            }
            return response.body.string()
        }
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}

/** A WMO weather code, described for the UI. */
data class WeatherLook(
    @androidx.annotation.DrawableRes val iconRes: Int,
    @androidx.annotation.StringRes val labelRes: Int,
)

/**
 * Open-Meteo's WMO weather code, as a Material Symbol and a label.
 *
 * The codes are grouped rather than enumerated one by one: the distinction
 * between "slight" and "moderate" drizzle is not worth a chip's width.
 */
fun weatherLook(code: Int): WeatherLook = when (code) {
    0          -> WeatherLook(R.drawable.ic_glance_weather_clear, R.string.weather_clear)
    1, 2       -> WeatherLook(R.drawable.ic_glance_weather_partly_cloudy, R.string.weather_partly_cloudy)
    3          -> WeatherLook(R.drawable.ic_glance_weather_cloudy, R.string.weather_cloudy)
    45, 48     -> WeatherLook(R.drawable.ic_glance_weather_fog, R.string.weather_fog)
    in 51..57  -> WeatherLook(R.drawable.ic_glance_weather_rain, R.string.weather_drizzle)
    in 61..67  -> WeatherLook(R.drawable.ic_glance_weather_rain, R.string.weather_rain)
    in 71..77  -> WeatherLook(R.drawable.ic_glance_weather_snow, R.string.weather_snow)
    in 80..82  -> WeatherLook(R.drawable.ic_glance_weather_rain, R.string.weather_showers)
    in 85..86  -> WeatherLook(R.drawable.ic_glance_weather_snow, R.string.weather_snow_showers)
    in 95..99  -> WeatherLook(R.drawable.ic_glance_weather_storm, R.string.weather_thunderstorm)
    else       -> WeatherLook(R.drawable.ic_glance_weather_unknown, R.string.weather_unknown)
}
