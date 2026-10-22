package com.deepak.deeplauncher.model;

import android.graphics.drawable.Drawable;

public class AppItem {
    private final String label;
    private final String packageName;
    private final String activityName;
    private final Drawable iconOrBanner;

    public AppItem(String label, String packageName, String activityName, Drawable iconOrBanner) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.iconOrBanner = iconOrBanner;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    public Drawable getIconOrBanner() {
        return iconOrBanner;
    }
}
