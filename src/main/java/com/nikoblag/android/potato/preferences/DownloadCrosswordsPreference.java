package com.nikoblag.android.potato.preferences;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;
import com.nikoblag.android.potato.util.Const;
import com.nikoblag.android.potato.util.Util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class DownloadCrosswordsPreference extends Preference {

    private ProgressDialog progressDialog;
    private DownloadTask downloadTask;

    public DownloadCrosswordsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        super.onClick();

        progressDialog = new ProgressDialog(getContext());
        progressDialog.setTitle("In progress...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(0);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                downloadTask.cancel(true);
            }
        });

        downloadTask = new DownloadTask(getContext());
        downloadTask.execute();
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;

        public DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager.WakeLock wl = Util.newWakeLock(context);
            wl.acquire();

            Properties props = new Properties();

            try {
                props.load(Util.downloadAndOpenFile(context, Const.POTATO_METAFILE_URL, ".meta"));
            } catch (Exception e) {
                wl.release();
                return e.getMessage();
            }

            int max = Integer.parseInt(props.getProperty("max_id"));

            try {
                for (int i = 1; i <= max; i++) {
                    String filename = i + ".jcw";
                    InputStream input = null;
                    OutputStream output = null;
                    HttpURLConnection connection = null;
                    try {
                        URL url = new URL(Const.POTATO_DROPBOX_URL + filename);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.connect();

                        // expect HTTP 200 OK, so we don't mistakenly save error report
                        // instead of the file
                        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                            return "Server returned HTTP " + connection.getResponseCode()
                                    + " " + connection.getResponseMessage();

                        int fileLength = connection.getContentLength();

                        // download the file
                        input = connection.getInputStream();
                        output = context.openFileOutput(filename, Context.MODE_PRIVATE);

                        byte data[] = new byte[4096];
                        long total = 0;
                        int count;
                        while ((count = input.read(data)) != -1) {
                            // allow canceling with back button
                            if (isCancelled())
                                return null;

                            total += count;

                            // publishing the progress....
                            if (fileLength > 0)
                                publishProgress((int) ((total * 100 / fileLength) * ((float) i / max)), i, max);
                            else
                                publishProgress(i * 100 / max, i, max);

                            output.write(data, 0, count);
                        }
                    } catch (Exception e) {
                        return e.toString();
                    } finally {
                        try {
                            if (output != null)
                                output.close();
                            if (input != null)
                                input.close();
                        }
                        catch (IOException ignored) { }

                        if (connection != null)
                            connection.disconnect();
                    }
                }
            } finally {
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
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(progress[2]);
            progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();

            if (result != null)
                Toast.makeText(context, "Download error: " + result, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show();

            notifyChanged();
        }

        @Override
        protected void onCancelled() {
            notifyChanged();
        }
    }
}
