package com.deepak.deeplauncher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherHelper {

    private static final String TAG = "DeepWeather";
    private static final String PREFS_NAME = "deep_launcher_weather";
    private static final String KEY_WEATHER = "cached_weather";
    private static final String KEY_TIMESTAMP = "cached_timestamp";
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour cache

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface WeatherCallback {
        void onWeatherLoaded(String weatherText);
    }

    public WeatherHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public void fetchWeather(WeatherCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cached = prefs.getString(KEY_WEATHER, null);
        long timestamp = prefs.getLong(KEY_TIMESTAMP, 0);

        if (cached != null && !cached.isEmpty()) {
            callback.onWeatherLoaded(cached);
        }

        // If cache is fresh, don't ping network
        if (System.currentTimeMillis() - timestamp < CACHE_TTL_MS && cached != null) {
            return;
        }

        executor.execute(() -> {
            String result = fetchFromNetwork();
            if (result != null && !result.isEmpty()) {
                // Save to cache
                prefs.edit()
                        .putString(KEY_WEATHER, result)
                        .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                        .apply();

                mainHandler.post(() -> callback.onWeatherLoaded(result));
            }
        });
    }

    private String fetchFromNetwork() {
        HttpURLConnection conn = null;
        try {
            // Using wttr.in with plaintext formatting
            URL url = new URL("https://wttr.in/?format=%c%20%t");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "curl/7.68.0");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String formatted = sb.toString().trim();
                // Format: replace "+25°C" with "25°C"
                formatted = formatted.replace("+", "");
                if (!formatted.isEmpty()) {
                    return formatted;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch weather: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }
}
