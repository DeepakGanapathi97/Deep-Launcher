package com.deepak.deeplauncher.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.deepak.deeplauncher.R;
import com.deepak.deeplauncher.model.AppItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    public interface ReorderListener {
        void onReorderStateChanged(boolean isReordering);
    }

    private final Context context;
    private final List<AppItem> appList = new ArrayList<>();
    private final PackageManager packageManager;
    private final ReorderListener reorderListener;
    private RecyclerView attachedRecyclerView;

    private boolean isReordering = false;
    private int reorderingPosition = -1;

    public AppAdapter(Context context, ReorderListener reorderListener) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.reorderListener = reorderListener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attachedRecyclerView = recyclerView;
    }

    public void setApps(List<AppItem> apps) {
        this.appList.clear();
        if (apps != null) {
            this.appList.addAll(apps);
        }
        notifyDataSetChanged();
    }

    public List<AppItem> getApps() {
        return appList;
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

        if (item.hasBanner() && item.getIconOrBanner() != null) {
            holder.imgBanner.setImageDrawable(item.getIconOrBanner());
            holder.imgBanner.setVisibility(View.VISIBLE);
            holder.layoutIconContent.setVisibility(View.GONE);
        } else {
            holder.imgBanner.setVisibility(View.GONE);
            holder.layoutIconContent.setVisibility(View.VISIBLE);
            if (item.getIconOrBanner() != null) {
                holder.imgIcon.setImageDrawable(item.getIconOrBanner());
            }
            holder.txtAppName.setText(item.getLabel());
        }

        boolean itemIsBeingMoved = isReordering && (reorderingPosition == position);
        if (itemIsBeingMoved) {
            holder.viewBorderHighlight.setBackgroundResource(R.drawable.bg_card_reordering);
            holder.badgeReorder.setVisibility(View.VISIBLE);
        } else {
            holder.viewBorderHighlight.setBackgroundResource(R.drawable.bg_card_selector);
            holder.badgeReorder.setVisibility(View.GONE);
        }

        // Hardware-accelerated focus animation with 3D Z-elevation (prevents clipping against neighbors)
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.08f : 1.0f;
            float elev = hasFocus ? 16f : 0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationZ(elev)
                    .setDuration(120)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            if (isReordering && reorderingPosition == holder.getAdapterPosition()) {
                holder.viewBorderHighlight.setBackgroundResource(R.drawable.bg_card_reordering);
            } else if (hasFocus) {
                holder.viewBorderHighlight.setBackgroundResource(R.drawable.bg_card_focused);
            } else {
                holder.viewBorderHighlight.setBackgroundResource(R.drawable.bg_card_normal);
            }
        });

        // Launch app on single click (when not in reorder mode)
        holder.itemView.setOnClickListener(v -> {
            if (isReordering) {
                exitReorderMode();
                return;
            }
            launchApp(item);
        });

        // Enter reorder mode on long press
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                enterReorderMode(pos);
                return true;
            }
            return false;
        });

        // D-Pad navigation handling during reorder mode & Menu button
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;

                // Menu key toggles reorder mode
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (isReordering) {
                        exitReorderMode();
                    } else {
                        enterReorderMode(pos);
                    }
                    return true;
                }

                if (isReordering) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        moveCurrentItemLeft();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        moveCurrentItemRight();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                        exitReorderMode();
                        return true;
                    }
                }
            }
            return false;
        });
    }

    public void enterReorderMode(int pos) {
        isReordering = true;
        reorderingPosition = pos;
        notifyItemChanged(pos);
        if (reorderListener != null) {
            reorderListener.onReorderStateChanged(true);
        }
        Toast.makeText(context, "Move Mode: Press ◀ / ▶ to move. Press OK to save.", Toast.LENGTH_SHORT).show();
    }

    public void enterReorderModeForCurrentFocus() {
        if (attachedRecyclerView != null) {
            View focused = attachedRecyclerView.getFocusedChild();
            if (focused != null) {
                int pos = attachedRecyclerView.getChildAdapterPosition(focused);
                if (pos != RecyclerView.NO_POSITION) {
                    enterReorderMode(pos);
                }
            }
        }
    }

    public void exitReorderMode() {
        if (!isReordering) return;
        isReordering = false;
        int oldPos = reorderingPosition;
        reorderingPosition = -1;
        if (oldPos != -1) {
            notifyItemChanged(oldPos);
        }
        if (reorderListener != null) {
            reorderListener.onReorderStateChanged(false);
        }
        saveAppOrder();
        Toast.makeText(context, "App order saved", Toast.LENGTH_SHORT).show();
    }

    public boolean isReordering() {
        return isReordering;
    }

    public void moveCurrentItemLeft() {
        if (reorderingPosition > 0) {
            int from = reorderingPosition;
            int to = reorderingPosition - 1;
            Collections.swap(appList, from, to);
            reorderingPosition = to;
            notifyItemMoved(from, to);
            saveAppOrder();

            if (attachedRecyclerView != null) {
                attachedRecyclerView.smoothScrollToPosition(to);
                attachedRecyclerView.post(() -> {
                    RecyclerView.ViewHolder vh = attachedRecyclerView.findViewHolderForAdapterPosition(to);
                    if (vh != null) {
                        vh.itemView.requestFocus();
                    }
                });
            }
        }
    }

    public void moveCurrentItemRight() {
        if (reorderingPosition >= 0 && reorderingPosition < appList.size() - 1) {
            int from = reorderingPosition;
            int to = reorderingPosition + 1;
            Collections.swap(appList, from, to);
            reorderingPosition = to;
            notifyItemMoved(from, to);
            saveAppOrder();

            if (attachedRecyclerView != null) {
                attachedRecyclerView.smoothScrollToPosition(to);
                attachedRecyclerView.post(() -> {
                    RecyclerView.ViewHolder vh = attachedRecyclerView.findViewHolderForAdapterPosition(to);
                    if (vh != null) {
                        vh.itemView.requestFocus();
                    }
                });
            }
        }
    }

    private void saveAppOrder() {
        List<String> pkgs = new ArrayList<>();
        for (AppItem item : appList) {
            pkgs.add(item.getPackageName());
        }
        SharedPreferences prefs = context.getSharedPreferences("deep_launcher_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("ordered_packages", TextUtils.join(",", pkgs)).apply();
    }

    private void launchApp(AppItem item) {
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
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgBanner;
        final ImageView imgIcon;
        final TextView txtAppName;
        final View viewBorderHighlight;
        final TextView badgeReorder;
        final LinearLayout layoutIconContent;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imgBanner = itemView.findViewById(R.id.imgBanner);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            viewBorderHighlight = itemView.findViewById(R.id.viewBorderHighlight);
            badgeReorder = itemView.findViewById(R.id.badgeReorder);
            layoutIconContent = itemView.findViewById(R.id.layoutIconContent);
        }
    }
}
