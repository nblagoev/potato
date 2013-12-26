package com.nikoblag.android.potato;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import com.nikoblag.android.potato.util.Const;

import com.actionbarsherlock.app.SherlockActivity;

public class MainActivity extends SherlockActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPref.getBoolean("auto_resume", true)) {
            if (prefs.getAll().isEmpty())
                newCrossword(null);
            else
                resumeCrossword(null);

            finish();
            return;
        }

        setContentView(R.layout.activity_main);
    }

    @SuppressWarnings("UnusedParameters")
    public void newCrossword(View view) {
        Intent intent = new Intent(getApplicationContext(), CrosswordActivity.class);
        intent.putExtra(Const.ACTIVITY_REQUEST, Const.ACTIVITY_REQUEST_NEW);
        startActivity(intent);
    }

    @SuppressWarnings("UnusedParameters")
    public void resumeCrossword(View view) {
        Intent intent = new Intent(getApplicationContext(), CrosswordActivity.class);
        intent.putExtra(Const.ACTIVITY_REQUEST, Const.ACTIVITY_REQUEST_RESUME);
        startActivity(intent);
    }

    @SuppressWarnings("UnusedParameters")
    public void exit(View view) {
        finish();
    }
}
