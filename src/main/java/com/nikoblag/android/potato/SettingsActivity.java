package com.nikoblag.android.potato;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.view.MenuItem;

import com.dropbox.sync.android.DbxAccountManager;
import com.nikoblag.android.potato.util.Const;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private DbxAccountManager mAccountManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        mAccountManager = DbxAccountManager.getInstance(getApplicationContext(),
                Const.DROPBOX_API_KEY, Const.DROPBOX_APP_KEY);
    }


    @Override
    protected void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, CrosswordActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(Const.ACTIVITY_REQUEST, Const.ACTIVITY_REQUEST_RESUME);
                startActivity(intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("dropbox_sync")) {
            if (sharedPreferences.getBoolean("dropbox_sync", false)) {
                mAccountManager.startLink(this, Const.REQUEST_LINK_TO_DBX);
                findPreference(key).setEnabled(false);
            } else {
                if (mAccountManager.hasLinkedAccount())
                    mAccountManager.unlink();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Const.REQUEST_LINK_TO_DBX) {
            final SwitchPreference p = (SwitchPreference) findPreference("dropbox_sync");
            if (resultCode != Activity.RESULT_OK)
                p.setChecked(false);

            p.setEnabled(true);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
