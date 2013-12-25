package com.nikoblag.android.potato.preferences;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class ClearCachePreference extends DialogPreference {

    public ClearCachePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        //TODO: Clear the cached files
    }

}
