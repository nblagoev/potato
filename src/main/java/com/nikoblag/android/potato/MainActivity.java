package com.nikoblag.android.potato;

import com.actionbarsherlock.app.SherlockActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends SherlockActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void newCrossword(View view) {
        Intent intent = new Intent(getApplicationContext(), CrosswordActivity.class);
        intent.putExtra("request", CrosswordActivityRequest.NEW);
        startActivity(intent);
    }

    public void resumeCrossword(View view) {
        Intent intent = new Intent(getApplicationContext(), CrosswordActivity.class);
        intent.putExtra("request", CrosswordActivityRequest.RESUME);
        startActivity(intent);
    }

    public void exit(View view) {
        finish();
    }
}
