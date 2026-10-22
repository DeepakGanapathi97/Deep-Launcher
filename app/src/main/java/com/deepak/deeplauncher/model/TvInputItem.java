package com.deepak.deeplauncher.model;

import android.net.Uri;

public class TvInputItem {
    private final String id;
    private final String label;
    private final Uri uri;

    public TvInputItem(String id, String label, Uri uri) {
        this.id = id;
        this.label = label;
        this.uri = uri;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Uri getUri() {
        return uri;
    }
}
