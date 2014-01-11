package com.nikoblag.android.potato.preferences;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;
import com.dropbox.sync.android.*;
import com.nikoblag.android.potato.R;
import com.nikoblag.android.potato.util.Const;
import com.nikoblag.android.potato.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class AuthorPreference extends Preference {

    private ProgressDialog progressDialog;

    private static int COUNTER = 0;

    public AuthorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        super.onClick();

        if (COUNTER++ == 10) {
            COUNTER = 0;
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.dialog_clear_data_confirm_title)
                    .setMessage(R.string.dialog_clear_data_confirm)
                    .setCancelable(false)
                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            doClear();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {}
                    });
            builder.create().show();

        }
    }

    private void doClear() {
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setTitle(null);
        progressDialog.setMessage(getContext().getString(R.string.dialog_clear_data));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        new DropboxClearTask(getContext().getApplicationContext()).execute();
    }

    private class DropboxClearTask extends AsyncTask<String, Integer, String> {

        private Context context;

        public DropboxClearTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... param) {
            PowerManager.WakeLock wl = Util.newWakeLock(context);
            wl.acquire();

            DbxAccountManager accMngr = DbxAccountManager.getInstance(context, Const.DROPBOX_API_KEY, Const.DROPBOX_APP_KEY);
            DbxDatastore dbxDatastore = null;
            try {
                if (accMngr.hasLinkedAccount()) {
                    dbxDatastore = DbxDatastore.openDefault(accMngr.getLinkedAccount());
                    for (DbxTable table : dbxDatastore.getTables()) {
                        for (DbxRecord record : table.query()) {
                            record.deleteRecord();
                        }
                    }
                    dbxDatastore.sync();
                }
            } catch (DbxException e) {
                return e.getMessage();
            } finally {
                dbxDatastore.close();
                wl.release();
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();

            if (result != null)
                Toast.makeText(context, "Error: " + result, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(context, "Datastores cleared", Toast.LENGTH_SHORT).show();

            notifyChanged();
        }
    }
}
