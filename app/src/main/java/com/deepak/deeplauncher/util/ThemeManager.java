package com.deepak.deeplauncher.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public class ThemeManager {

    private static final String PREFS_NAME = "deep_launcher_theme";
    private static final String KEY_THEME_MODE = "theme_mode";

    public static final int MODE_AUTO = 0;   // Dynamic: White on Daylight, OLED Dark on Night
    public static final int MODE_DAY = 1;    // Always Daylight White
    public static final int MODE_NIGHT = 2;  // Always OLED Dark

    public static int getThemeMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, MODE_AUTO);
    }

    public static void setThemeMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public static boolean isDayMode(Context context) {
        int mode = getThemeMode(context);
        if (mode == MODE_DAY) return true;
        if (mode == MODE_NIGHT) return false;

        // Auto mode based on daylight: 6:00 AM (6) to 6:00 PM (18) is Day, 18:00 to 6:00 is Night
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return (hour >= 6 && hour < 18);
    }

    public static int toggleNextMode(Context context) {
        int current = getThemeMode(context);
        int next = (current + 1) % 3;
        setThemeMode(context, next);
        return next;
    }
}
