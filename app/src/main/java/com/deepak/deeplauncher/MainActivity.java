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

public class MainActivity extends Activity implements PackageReceiver.PackageChangeListener {

    private RecyclerView recyclerApps;
    private RecyclerView recyclerInputs;
    private LinearLayout layoutInputsContainer;
    private TextView tvClock;
    private TextView tvWeather;
    private TextView tvReorderHint;
    private FrameLayout btnSettings;

    private AppAdapter appAdapter;
    private TvInputAdapter tvInputAdapter;
    private WeatherHelper weatherHelper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver timeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerViews();
        updateClock();

        loadAppsAndInputs();

        PackageReceiver.setListener(this);
    }

    private void initViews() {
        recyclerApps = findViewById(R.id.recyclerApps);
        recyclerInputs = findViewById(R.id.recyclerInputs);
        layoutInputsContainer = findViewById(R.id.layoutInputsContainer);
        tvClock = findViewById(R.id.tvClock);
        tvWeather = findViewById(R.id.tvWeather);
        tvReorderHint = findViewById(R.id.tvReorderHint);
        btnSettings = findViewById(R.id.btnSettings);

        weatherHelper = new WeatherHelper(this);

        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.15f : 1.0f;
            float elev = hasFocus ? 16f : 0f;
            v.animate().scaleX(scale).scaleY(scale).translationZ(elev).setDuration(120).start();
        });

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
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                if (appAdapter != null) {
                    if (appAdapter.isReordering()) {
                        appAdapter.exitReorderMode();
                    } else {
                        appAdapter.enterReorderModeForCurrentFocus();
                    }
                    return true;
                }
            }

            if (appAdapter != null && appAdapter.isReordering()) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    appAdapter.moveCurrentItemLeft();
                    return true;
                } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    appAdapter.moveCurrentItemRight();
                    return true;
                } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    appAdapter.exitReorderMode();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
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
        loadAppsAndInputs();
    }

    private void loadAppsAndInputs() {
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

            if (savedOrderStr != null && !savedOrderStr.isEmpty()) {
                List<String> savedOrder = Arrays.asList(savedOrderStr.split(","));
                items.sort(Comparator.comparingInt(a -> {
                    int idx = savedOrder.indexOf(a.getPackageName());
                    return idx != -1 ? idx : 999;
                }));
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
