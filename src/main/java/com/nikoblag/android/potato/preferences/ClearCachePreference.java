package com.nikoblag.android.potato.preferences;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;

public class ClearCachePreference extends DialogPreference {

    public ClearCachePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            File[] files = getContext().getFilesDir().listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith("jcw");
                }
            });

            for (File file : files) {
                file.delete();
            }

            setEnabled(false);

            Toast.makeText(getContext(), "Cache cleared", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        File[] files = getContext().getFilesDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith("jcw");
            }
        });

        if (files.length < 1)
            setEnabled(false);
        else
            setEnabled(true);

        return super.onCreateView(parent);
    }
}
