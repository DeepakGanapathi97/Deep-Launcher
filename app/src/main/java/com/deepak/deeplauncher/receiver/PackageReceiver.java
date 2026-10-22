package com.deepak.deeplauncher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.deepak.deeplauncher.MainActivity;

public class PackageReceiver extends BroadcastReceiver {

    public interface PackageChangeListener {
        void onPackagesChanged();
    }

    private static PackageChangeListener listener;

    public static void setListener(PackageChangeListener l) {
        listener = l;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            if (listener != null) {
                listener.onPackagesChanged();
            }
        }
    }
}
