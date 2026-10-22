package com.deepak.deeplauncher.util;

import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import com.deepak.deeplauncher.model.TvInputItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TvInputHelper {

    public static List<TvInputItem> loadPassthroughInputs(Context context) {
        List<TvInputItem> list = new ArrayList<>();
        try {
            TvInputManager tvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (tvInputManager == null) {
                return Collections.emptyList();
            }

            List<TvInputInfo> inputList = tvInputManager.getTvInputList();
            if (inputList == null) {
                return Collections.emptyList();
            }

            for (TvInputInfo info : inputList) {
                if (info.isPassthroughInput()) {
                    CharSequence labelSeq = info.loadLabel(context);
                    String label = (labelSeq != null && labelSeq.length() > 0)
                            ? labelSeq.toString()
                            : formatDefaultInputLabel(info.getId());

                    Uri uri = TvContract.buildChannelUriForPassthroughInput(info.getId());
                    list.add(new TvInputItem(info.getId(), label, uri));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static String formatDefaultInputLabel(String inputId) {
        if (inputId == null) return "Input";
        String lower = inputId.toLowerCase();
        if (lower.contains("hdmi1") || lower.contains("hdmi_1")) return "HDMI 1";
        if (lower.contains("hdmi2") || lower.contains("hdmi_2")) return "HDMI 2";
        if (lower.contains("hdmi3") || lower.contains("hdmi_3")) return "HDMI 3";
        if (lower.contains("hdmi4") || lower.contains("hdmi_4")) return "HDMI 4";
        if (lower.contains("av")) return "AV";
        if (lower.contains("composite")) return "Composite";
        return "Input";
    }
}
