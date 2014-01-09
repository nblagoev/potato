package com.nikoblag.android.potato;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import android.widget.Toast;
import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxDatastore;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxRecord;
import com.dropbox.sync.android.DbxTable;
import com.nikoblag.android.potato.util.Const;

import com.actionbarsherlock.app.SherlockActivity;

public class MainActivity extends SherlockActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        DbxAccountManager accMngr = DbxAccountManager.getInstance(getApplicationContext(),
                Const.DROPBOX_API_KEY, Const.DROPBOX_APP_KEY);

        if (sharedPref.getBoolean("auto_resume", true)) {
            if (accMngr.hasLinkedAccount()) {
                try {
                    DbxDatastore dbxDatastore = DbxDatastore.openDefault(accMngr.getLinkedAccount());
                    DbxRecord active = dbxDatastore.getTable("state").getOrInsert("active");
                    boolean toResume = active != null && active.hasField("cwid");
                    dbxDatastore.close();
                    if (toResume)
                        resumeCrossword(null);
                    else
                        newCrossword(null);
                }  catch (DbxException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                if (prefs.getAll().isEmpty())
                    newCrossword(null);
                else
                    resumeCrossword(null);
            }
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
