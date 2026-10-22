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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private FrameLayout btnSettings;

    private AppAdapter appAdapter;
    private TvInputAdapter tvInputAdapter;

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
        btnSettings = findViewById(R.id.btnSettings);

        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.15f : 1.0f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
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
        appAdapter = new AppAdapter(this);
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
    public void onBackPressed() {
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

            // Exclude self and known system overlays
            String myPkg = getPackageName();

            // Process Leanback apps
            for (ResolveInfo info : rawApps) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(myPkg) || seenPackages.contains(pkg)) continue;

                seenPackages.add(pkg);
                String label = info.loadLabel(pm).toString();
                Drawable banner = info.activityInfo.loadBanner(pm);
                if (banner == null) {
                    banner = info.activityInfo.loadIcon(pm);
                }
                items.add(new AppItem(label, pkg, info.activityInfo.name, banner));
            }

            // Process non-Leanback sideloaded apps
            for (ResolveInfo info : standardApps) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(myPkg) || seenPackages.contains(pkg)) continue;

                seenPackages.add(pkg);
                String label = info.loadLabel(pm).toString();
                Drawable icon = info.activityInfo.loadBanner(pm);
                if (icon == null) {
                    icon = info.activityInfo.loadIcon(pm);
                }
                items.add(new AppItem(label, pkg, info.activityInfo.name, icon));
            }

            // Sort alphabetically for fast predictable access
            Collections.sort(items, Comparator.comparing(a -> a.getLabel().toLowerCase()));

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
