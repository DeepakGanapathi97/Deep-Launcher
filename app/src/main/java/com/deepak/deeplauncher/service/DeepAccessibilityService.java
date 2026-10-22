package com.deepak.deeplauncher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class DeepAccessibilityService extends AccessibilityService {

    private static final String TAG = "DeepKeyMapper";
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long lastMicPressTime = 0;
    private Runnable pendingMicAction = null;
    private static final long DOUBLE_CLICK_TIMEOUT = 380; // ms

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "DeepAccessibilityService created and active");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        android.accessibilityservice.AccessibilityServiceInfo info = new android.accessibilityservice.AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 50;
        setServiceInfo(info);
        Log.d(TAG, "DeepAccessibilityService connected and key filter flags applied");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op: Only interested in hardware key filtering
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "DeepAccessibilityService interrupted");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "Intercepted KeyEvent DOWN: " + keyCode);

            // 1. PatchWall Button -> Launch YouTube
            if (isPatchWallKey(keyCode)) {
                launchYouTube();
                return true; // Consume event completely
            }

            // 2. Mic / Google Assistant Button -> Direct Play/Pause Toggle (or double-click Settings)
            if (isMicKey(keyCode)) {
                handleMicKey();
                return true; // Consume event completely
            }
        } else if (action == KeyEvent.ACTION_UP) {
            // Consume key up for intercepted buttons so system doesn't trigger default action
            if (isPatchWallKey(keyCode) || isMicKey(keyCode)) {
                return true;
            }
        }

        return super.onKeyEvent(event);
    }

    private boolean isPatchWallKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_PROG_RED          // 183
                || keyCode == KeyEvent.KEYCODE_BUTTON_4      // 191
                || keyCode == KeyEvent.KEYCODE_BUTTON_5      // 192
                || keyCode == 284;                           // KEYCODE_ALL_APPS (API 28+)
    }

    private boolean isMicKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ASSIST            // 219
                || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST  // 231
                || keyCode == KeyEvent.KEYCODE_SEARCH;       // 84
    }

    private void launchYouTube() {
        Log.d(TAG, "Launching YouTube directly from PatchWall key");
        try {
            // Check SmartTube first if user installed it, otherwise official YouTube TV
            String[] targetPackages = new String[]{
                    "org.smarttube.stable",
                    "com.teamsmart.videomanager.tv",
                    "com.liskovsoft.videomanager",
                    "com.google.android.youtube.tv"
            };

            Intent launchIntent = null;
            for (String pkg : targetPackages) {
                launchIntent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (launchIntent == null) {
                    launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                }
                if (launchIntent != null) {
                    break;
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching YouTube: " + e.getMessage());
        }
    }

    private void handleMicKey() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMicPressTime < DOUBLE_CLICK_TIMEOUT) {
            // Double click detected -> Open TV Settings
            if (pendingMicAction != null) {
                handler.removeCallbacks(pendingMicAction);
                pendingMicAction = null;
            }
            lastMicPressTime = 0;
            openSettings();
        } else {
            // Potential single click -> Schedule Media Play/Pause toggle
            lastMicPressTime = currentTime;
            pendingMicAction = () -> {
                toggleMediaPlayPause();
                pendingMicAction = null;
            };
            handler.postDelayed(pendingMicAction, DOUBLE_CLICK_TIMEOUT);
        }
    }

    private void toggleMediaPlayPause() {
        Log.d(TAG, "Toggling media Play/Pause via Mic key");
        try {
            if (audioManager != null) {
                KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                audioManager.dispatchMediaKeyEvent(down);
                audioManager.dispatchMediaKeyEvent(up);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dispatching media play/pause: " + e.getMessage());
        }
    }

    private void openSettings() {
        Log.d(TAG, "Opening Quick Settings via Mic double-click");
        try {
            Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening settings: " + e.getMessage());
        }
    }
}
