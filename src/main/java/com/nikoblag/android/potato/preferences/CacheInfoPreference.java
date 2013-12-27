package com.nikoblag.android.potato.preferences;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import com.nikoblag.android.potato.R;
import com.nikoblag.android.potato.util.Util;

import java.io.File;
import java.io.FileFilter;

public class CacheInfoPreference extends Preference {
    public CacheInfoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        setSummary(getCacheSizeSummary());

        return super.onCreateView(parent);
    }

    public String getCacheSizeSummary() {
        File[] files = getContext().getFilesDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith("jcw");
            }
        });

        long size = 0;
        for (File file : files) {
            size += file.length();
        }

        String summary = getContext().getResources().getString(R.string.pref_summary_cache_info);

        return String.format(summary, Util.sizeToString(size));
    }
}
