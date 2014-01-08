package com.nikoblag.android.potato.preferences;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.nikoblag.android.potato.R;
import com.nikoblag.android.potato.util.Util;

import java.io.File;
import java.io.FileFilter;


public class ClearCachePreference extends DialogPreference {

    private ProgressDialog progressDialog;

    public ClearCachePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setTitle(null);
            progressDialog.setMessage(getContext().getString(R.string.pref_dialog_title_clearing_cache));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);

            new ClearCacheTask(getContext()).execute();
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


    private class ClearCacheTask extends AsyncTask<Void, Void, Void> {

        private Context context;

        public ClearCacheTask(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during clearing
            PowerManager.WakeLock wl = Util.newWakeLock(context);
            wl.acquire();

            File[] files = getContext().getFilesDir().listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith("jcw");
                }
            });

            for (File file : files) {
                file.delete();
            }

            wl.release();

            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void result) {
            progressDialog.dismiss();
            progressDialog = null;
            setEnabled(false);
            Toast.makeText(getContext(), "Cache cleared", Toast.LENGTH_SHORT).show();
            notifyChanged();
        }

        //@Override
        //protected void onCancelled() {
        //    notifyChanged();
        //}
    }
}
