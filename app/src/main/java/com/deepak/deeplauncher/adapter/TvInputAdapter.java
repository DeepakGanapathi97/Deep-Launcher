package com.deepak.deeplauncher.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.deepak.deeplauncher.R;
import com.deepak.deeplauncher.model.TvInputItem;

import java.util.ArrayList;
import java.util.List;

public class TvInputAdapter extends RecyclerView.Adapter<TvInputAdapter.InputViewHolder> {

    private final Context context;
    private final List<TvInputItem> inputList = new ArrayList<>();

    public TvInputAdapter(Context context) {
        this.context = context;
    }

    public void setInputs(List<TvInputItem> inputs) {
        this.inputList.clear();
        if (inputs != null) {
            this.inputList.addAll(inputs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public InputViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_input, parent, false);
        return new InputViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InputViewHolder holder, int position) {
        TvInputItem item = inputList.get(position);
        holder.txtInputName.setText(item.getLabel());

        // TV D-pad focus scaling
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.08f : 1.0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .start();
        });

        // Switch to HDMI/AV Input
        holder.itemView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, item.getUri());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "Cannot switch to " + item.getLabel(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return inputList.size();
    }

    static class InputViewHolder extends RecyclerView.ViewHolder {
        final TextView txtInputName;

        InputViewHolder(@NonNull View itemView) {
            super(itemView);
            txtInputName = itemView.findViewById(R.id.txtInputName);
        }
    }
}
