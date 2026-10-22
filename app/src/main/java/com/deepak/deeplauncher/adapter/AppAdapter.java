package com.deepak.deeplauncher.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.deepak.deeplauncher.R;
import com.deepak.deeplauncher.model.AppItem;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private final Context context;
    private final List<AppItem> appList = new ArrayList<>();
    private final PackageManager packageManager;

    public AppAdapter(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
    }

    public void setApps(List<AppItem> apps) {
        this.appList.clear();
        if (apps != null) {
            this.appList.addAll(apps);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppItem item = appList.get(position);
        holder.txtAppName.setText(item.getLabel());

        if (item.getIconOrBanner() != null) {
            holder.imgBanner.setImageDrawable(item.getIconOrBanner());
        }

        // Hardware-accelerated TV D-pad focus scaling (Projectivy reverse-engineered behavior)
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.08f : 1.0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .start();
        });

        // Instant click & launch
        holder.itemView.setOnClickListener(v -> {
            try {
                Intent intent = packageManager.getLeanbackLaunchIntentForPackage(item.getPackageName());
                if (intent == null) {
                    intent = packageManager.getLaunchIntentForPackage(item.getPackageName());
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Cannot open " + item.getLabel(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Failed to launch app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgBanner;
        final TextView txtAppName;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imgBanner = itemView.findViewById(R.id.imgBanner);
            txtAppName = itemView.findViewById(R.id.txtAppName);
        }
    }
}
