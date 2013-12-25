package com.nikoblag.android.potato;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.actionbarsherlock.app.SherlockActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.nikoblag.android.potato.util.Const;

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

    public void newCrossword(View view) {
        Intent intent = new Intent(getApplicationContext(), CrosswordActivity.class);
        intent.putExtra(Const.ACTIVITY_REQUEST, Const.ACTIVITY_REQUEST_NEW);
        startActivity(intent);
    }

    public void resumeCrossword(View view) {
        Intent intent = new Intent(getApplicationContext(), CrosswordActivity.class);
        intent.putExtra(Const.ACTIVITY_REQUEST, Const.ACTIVITY_REQUEST_RESUME);
        startActivity(intent);
    }

    public void exit(View view) {
        finish();
    }
}
