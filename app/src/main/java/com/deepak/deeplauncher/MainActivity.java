package com.deepak.deeplauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepak.deeplauncher.adapter.AppAdapter;
import com.deepak.deeplauncher.adapter.TvInputAdapter;
import com.deepak.deeplauncher.model.AppItem;
import com.deepak.deeplauncher.model.TvInputItem;
import com.deepak.deeplauncher.receiver.PackageReceiver;
import com.deepak.deeplauncher.util.TvInputHelper;
import com.deepak.deeplauncher.util.WeatherHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Color;
import android.widget.ImageView;
import com.deepak.deeplauncher.util.ThemeManager;

public class MainActivity extends Activity implements PackageReceiver.PackageChangeListener {

    private LinearLayout rootLayout;
    private RecyclerView recyclerApps;
    private RecyclerView recyclerInputs;
    private LinearLayout layoutInputsContainer;
    private TextView tvLauncherTitle;
    private TextView tvClock;
    private TextView tvWeather;
    private TextView tvReorderHint;
    private TextView headerApps;
    private TextView headerInputs;
    private FrameLayout btnSettings;
    private ImageView imgSettingsIcon;
    private FrameLayout btnTheme;
    private ImageView imgThemeIcon;

    private Runnable longPressRunnable = null;
    private boolean isDpadCenterDown = false;
    private boolean longPressTriggered = false;
    private boolean ignoreNextDpadCenterUp = false;
    private long dpadCenterDownTime = 0;

    private AppAdapter appAdapter;
    private TvInputAdapter tvInputAdapter;
    private WeatherHelper weatherHelper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver timeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
            applyTheme();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerViews();
        updateClock();
        applyTheme();

        loadAppsAndInputs();

        PackageReceiver.setListener(this);
    }

    private void initViews() {
        rootLayout = findViewById(R.id.rootLayout);
        recyclerApps = findViewById(R.id.recyclerApps);
        recyclerInputs = findViewById(R.id.recyclerInputs);
        layoutInputsContainer = findViewById(R.id.layoutInputsContainer);
        tvLauncherTitle = findViewById(R.id.tvLauncherTitle);
        tvClock = findViewById(R.id.tvClock);
        tvWeather = findViewById(R.id.tvWeather);
        tvReorderHint = findViewById(R.id.tvReorderHint);
        headerApps = findViewById(R.id.headerApps);
        headerInputs = findViewById(R.id.headerInputs);
        btnSettings = findViewById(R.id.btnSettings);
        imgSettingsIcon = findViewById(R.id.imgSettingsIcon);
        btnTheme = findViewById(R.id.btnTheme);
        imgThemeIcon = findViewById(R.id.imgThemeIcon);

        weatherHelper = new WeatherHelper(this);

        tvLauncherTitle.setFocusable(false);
        tvLauncherTitle.setClickable(false);

        btnTheme.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.15f : 1.0f;
            float elev = hasFocus ? 16f : 0f;
            v.animate().scaleX(scale).scaleY(scale).translationZ(elev).setDuration(120).start();
        });
        btnTheme.setOnClickListener(v -> toggleTheme());
        btnTheme.setNextFocusRightId(R.id.btnSettings);
        btnTheme.setNextFocusDownId(R.id.recyclerApps);

        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.15f : 1.0f;
            float elev = hasFocus ? 16f : 0f;
            v.animate().scaleX(scale).scaleY(scale).translationZ(elev).setDuration(120).start();
        });
        btnSettings.setNextFocusLeftId(R.id.btnTheme);
        btnSettings.setNextFocusDownId(R.id.recyclerApps);

        btnSettings.setOnClickListener(v -> {
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settingsIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open Settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyTheme() {
        boolean isDay = ThemeManager.isDayMode(this);
        int mode = ThemeManager.getThemeMode(this);

        // Update Theme button icon according to mode
        if (imgThemeIcon != null) {
            if (mode == ThemeManager.MODE_AUTO) {
                imgThemeIcon.setImageResource(R.drawable.ic_theme_auto);
            } else if (mode == ThemeManager.MODE_DAY) {
                imgThemeIcon.setImageResource(R.drawable.ic_theme_day);
            } else {
                imgThemeIcon.setImageResource(R.drawable.ic_theme_night);
            }
        }

        if (isDay) {
            rootLayout.setBackgroundColor(Color.parseColor("#F3F4F8"));
            tvLauncherTitle.setTextColor(Color.parseColor("#0F172A"));
            tvClock.setTextColor(Color.parseColor("#0F172A"));
            tvWeather.setTextColor(Color.parseColor("#475569"));
            headerApps.setTextColor(Color.parseColor("#64748B"));
            headerInputs.setTextColor(Color.parseColor("#64748B"));
            btnSettings.setBackgroundResource(R.drawable.bg_button_settings_light);
            btnTheme.setBackgroundResource(R.drawable.bg_button_settings_light);
            if (imgSettingsIcon != null) {
                imgSettingsIcon.setColorFilter(Color.parseColor("#0F172A"));
            }
            if (imgThemeIcon != null) {
                imgThemeIcon.setColorFilter(Color.parseColor("#0F172A"));
            }
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#0A0A0C"));
            tvLauncherTitle.setTextColor(Color.parseColor("#F8FAFC"));
            tvClock.setTextColor(Color.parseColor("#F8FAFC"));
            tvWeather.setTextColor(Color.parseColor("#94A3B8"));
            headerApps.setTextColor(Color.parseColor("#94A3B8"));
            headerInputs.setTextColor(Color.parseColor("#94A3B8"));
            btnSettings.setBackgroundResource(R.drawable.bg_button_settings);
            btnTheme.setBackgroundResource(R.drawable.bg_button_settings);
            if (imgSettingsIcon != null) {
                imgSettingsIcon.setColorFilter(Color.parseColor("#FFFFFF"));
            }
            if (imgThemeIcon != null) {
                imgThemeIcon.setColorFilter(Color.parseColor("#FFFFFF"));
            }
        }

        if (appAdapter != null) {
            appAdapter.setDayMode(isDay);
        }
        if (tvInputAdapter != null) {
            tvInputAdapter.setDayMode(isDay);
        }
    }

    private void toggleTheme() {
        int nextMode = ThemeManager.toggleNextMode(this);
        applyTheme();
        String modeName = (nextMode == ThemeManager.MODE_AUTO) ? "Auto (Time-based Daylight/Night)"
                : (nextMode == ThemeManager.MODE_DAY) ? "Daylight White" : "OLED Pure Dark";
        Toast.makeText(this, "Theme: " + modeName, Toast.LENGTH_SHORT).show();
    }

    private void setupRecyclerViews() {
        LinearLayoutManager appsLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerApps.setLayoutManager(appsLayoutManager);
        appAdapter = new AppAdapter(this, isReordering -> {
            if (tvReorderHint != null) {
                tvReorderHint.setVisibility(isReordering ? View.VISIBLE : View.GONE);
            }
        });
        recyclerApps.setAdapter(appAdapter);

        LinearLayoutManager inputsLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerInputs.setLayoutManager(inputsLayoutManager);
        tvInputAdapter = new TvInputAdapter(this);
        recyclerInputs.setAdapter(tvInputAdapter);
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        tvClock.setText(sdf.format(new Date()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(timeTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        updateClock();
        applyTheme();
        loadAppsAndInputs();

        if (weatherHelper != null && tvWeather != null) {
            weatherHelper.fetchWeather(weatherText -> {
                if (weatherText != null && !weatherText.isEmpty()) {
                    tvWeather.setText(weatherText);
                    tvWeather.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(timeTickReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PackageReceiver.setListener(null);
        executor.shutdown();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
            int pos = getFocusedAppPosition();
            if (pos != -1 && appAdapter != null && !appAdapter.isReordering()) {
                longPressTriggered = true;
                appAdapter.enterReorderMode(pos);
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // 1. Menu button: instant toggle for Reorder Mode
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (appAdapter != null) {
                    if (appAdapter.isReordering()) {
                        appAdapter.exitReorderMode();
                    } else {
                        int pos = getFocusedAppPosition();
                        if (pos != -1) {
                            appAdapter.enterReorderMode(pos);
                        }
                    }
                }
            }
            return true;
        }

        // 2. When currently in Reorder Mode:
        if (appAdapter != null && appAdapter.isReordering()) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    appAdapter.moveCurrentItemLeft();
                    return true;
                } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    appAdapter.moveCurrentItemRight();
                    return true;
                } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    ignoreNextDpadCenterUp = true;
                    appAdapter.exitReorderMode();
                    return true;
                }
            } else if (action == android.view.KeyEvent.ACTION_UP) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (ignoreNextDpadCenterUp) {
                        ignoreNextDpadCenterUp = false;
                        return true;
                    }
                }
            }
            // Consume vertical D-pad navigation so the user doesn't accidentally navigate away
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // 3. When NOT in Reorder Mode: Handle Long Press on OK / DPAD_CENTER / ENTER
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {

            if (action == android.view.KeyEvent.ACTION_DOWN) {
                int focusedPos = getFocusedAppPosition();

                if (focusedPos != -1) {
                    // Fast path 1: Event already carries FLAG_LONG_PRESS (from ADB or TV key repeat)
                    if (event.isLongPress()) {
                        longPressTriggered = true;
                        appAdapter.enterReorderMode(focusedPos);
                        return true;
                    }

                    if (!isDpadCenterDown) {
                        isDpadCenterDown = true;
                        longPressTriggered = false;
                        dpadCenterDownTime = android.os.SystemClock.uptimeMillis();
                        event.startTracking();

                        if (longPressRunnable != null) {
                            mainHandler.removeCallbacks(longPressRunnable);
                        }
                        longPressRunnable = () -> {
                            if (isDpadCenterDown && appAdapter != null && !appAdapter.isReordering()) {
                                int currentPos = getFocusedAppPosition();
                                if (currentPos != -1) {
                                    longPressTriggered = true;
                                    appAdapter.enterReorderMode(currentPos);
                                }
                            }
                        };
                        mainHandler.postDelayed(longPressRunnable, 450);
                    } else if (event.getRepeatCount() >= 1 && !longPressTriggered) {
                        long heldDuration = android.os.SystemClock.uptimeMillis() - dpadCenterDownTime;
                        if (heldDuration >= 350 || event.getRepeatCount() >= 2) {
                            if (longPressRunnable != null) {
                                mainHandler.removeCallbacks(longPressRunnable);
                                longPressRunnable = null;
                            }
                            longPressTriggered = true;
                            appAdapter.enterReorderMode(focusedPos);
                            return true;
                        }
                    }
                }
            } else if (action == android.view.KeyEvent.ACTION_UP) {
                isDpadCenterDown = false;
                long heldDuration = android.os.SystemClock.uptimeMillis() - dpadCenterDownTime;

                if (longPressRunnable != null) {
                    mainHandler.removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }
                if (ignoreNextDpadCenterUp) {
                    ignoreNextDpadCenterUp = false;
                    return true;
                }
                if (longPressTriggered || event.isCanceled() || heldDuration >= 450) {
                    longPressTriggered = false;
                    return true; // Consume event to prevent click / launchApp
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private int getFocusedAppPosition() {
        if (recyclerApps == null || appAdapter == null) return -1;
        View focused = recyclerApps.getFocusedChild();
        if (focused != null) {
            int pos = recyclerApps.getChildAdapterPosition(focused);
            if (pos != RecyclerView.NO_POSITION) {
                return pos;
            }
        }

        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            View parent = currentFocus;
            while (parent != null && parent.getParent() != recyclerApps) {
                if (parent.getParent() instanceof View) {
                    parent = (View) parent.getParent();
                } else {
                    break;
                }
            }
            if (parent != null && parent.getParent() == recyclerApps) {
                int pos = recyclerApps.getChildAdapterPosition(parent);
                if (pos != RecyclerView.NO_POSITION) {
                    return pos;
                }
            }
        }
        return -1;
    }

    @Override
    public void onBackPressed() {
        if (appAdapter != null && appAdapter.isReordering()) {
            appAdapter.exitReorderMode();
            return;
        }
        // As a Home launcher, back button focuses the first app tile
        focusFirstApp();
    }

    @Override
    public void onPackagesChanged() {
        if (appAdapter != null && appAdapter.isReordering()) {
            return;
        }
        loadAppsAndInputs();
    }

    private void loadAppsAndInputs() {
        if (appAdapter != null && appAdapter.isReordering()) {
            return;
        }
        executor.execute(() -> {
            PackageManager pm = getPackageManager();

            // 1. Query Leanback Launcher Apps
            Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
            leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> rawApps = pm.queryIntentActivities(leanbackIntent, 0);

            // 2. Query Standard Launcher Apps (fallback for sideloaded apps)
            Intent standardIntent = new Intent(Intent.ACTION_MAIN, null);
            standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> standardApps = pm.queryIntentActivities(standardIntent, 0);

            Set<String> seenPackages = new HashSet<>();
            List<AppItem> items = new ArrayList<>();

            // Exclude self and secondary launchers (Projectivy) so only content apps show
            String myPkg = getPackageName();
            seenPackages.add(myPkg);
            seenPackages.add("com.spocky.projengmenu");
            seenPackages.add("com.google.android.tvlauncher");

            // Process Leanback apps
            for (ResolveInfo info : rawApps) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (seenPackages.contains(pkg)) continue;

                seenPackages.add(pkg);
                String label = info.loadLabel(pm).toString();
                Drawable banner = info.activityInfo.loadBanner(pm);
                boolean hasBanner = (banner != null);
                Drawable displayDrawable = hasBanner ? banner : info.activityInfo.loadIcon(pm);
                items.add(new AppItem(label, pkg, info.activityInfo.name, displayDrawable, hasBanner));
            }

            // Process non-Leanback sideloaded apps
            for (ResolveInfo info : standardApps) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (seenPackages.contains(pkg)) continue;

                seenPackages.add(pkg);
                String label = info.loadLabel(pm).toString();
                Drawable banner = info.activityInfo.loadBanner(pm);
                boolean hasBanner = (banner != null);
                Drawable displayDrawable = hasBanner ? banner : info.activityInfo.loadIcon(pm);
                items.add(new AppItem(label, pkg, info.activityInfo.name, displayDrawable, hasBanner));
            }

            // Check if user has saved a custom app order
            android.content.SharedPreferences prefs = getSharedPreferences("deep_launcher_prefs", Context.MODE_PRIVATE);
            String savedOrderStr = prefs.getString("ordered_packages", null);
            android.util.Log.d("DeepLauncher", "loadAppsAndInputs savedOrder: " + savedOrderStr);

            if (savedOrderStr != null && !savedOrderStr.isEmpty()) {
                List<String> savedOrder = Arrays.asList(savedOrderStr.split(","));
                items.sort((a, b) -> {
                    int idxA = savedOrder.indexOf(a.getPackageName());
                    int idxB = savedOrder.indexOf(b.getPackageName());
                    if (idxA != -1 && idxB != -1) {
                        return Integer.compare(idxA, idxB);
                    } else if (idxA != -1) {
                        return -1;
                    } else if (idxB != -1) {
                        return 1;
                    }
                    return a.getLabel().compareToIgnoreCase(b.getLabel());
                });
            } else {
                // Default Priority: SmartTube, YouTube, Hotstar, Prime Video, then alphabetical
                List<String> priorityPackages = Arrays.asList(
                        "org.smarttube.stable",
                        "com.google.android.youtube.tv",
                        "com.teamsmart.videomanager.tv",
                        "com.liskovsoft.videomanager",
                        "in.startv.hotstar",
                        "com.amazon.amazonvideo.livingroom"
                );

                items.sort((a, b) -> {
                    int idxA = priorityPackages.indexOf(a.getPackageName());
                    int idxB = priorityPackages.indexOf(b.getPackageName());
                    if (idxA != -1 && idxB != -1) {
                        return Integer.compare(idxA, idxB);
                    } else if (idxA != -1) {
                        return -1;
                    } else if (idxB != -1) {
                        return 1;
                    }
                    return a.getLabel().compareToIgnoreCase(b.getLabel());
                });
            }

            // Load TV Inputs (HDMI 1, HDMI 2, AV, etc.)
            List<TvInputItem> inputs = TvInputHelper.loadPassthroughInputs(MainActivity.this);

            // Post back to UI thread
            mainHandler.post(() -> {
                if (appAdapter != null && appAdapter.isReordering()) {
                    return;
                }
                appAdapter.setApps(items);

                if (inputs.isEmpty()) {
                    layoutInputsContainer.setVisibility(View.GONE);
                } else {
                    layoutInputsContainer.setVisibility(View.VISIBLE);
                    tvInputAdapter.setInputs(inputs);
                }

                focusFirstApp();
            });
        });
    }

    private void focusFirstApp() {
        recyclerApps.post(() -> {
            RecyclerView.ViewHolder vh = recyclerApps.findViewHolderForAdapterPosition(0);
            if (vh != null) {
                vh.itemView.requestFocus();
            }
        });
    }
}
