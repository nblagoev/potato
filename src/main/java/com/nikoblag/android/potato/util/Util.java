package com.nikoblag.android.potato.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public final class Util {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    /**
     * Generate a value suitable for use in {@link #setId(int)}.
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * (from the Android source; API 17+)
     *
     * @return a generated ID value
     */
    public static int generateViewId() {
        for (;;) {
            final int result = sNextGeneratedId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }

    public static int getBoxId(int i, int j) {
        return (i << 8) | j;
    }

    public static void downloadFile(Context context, String link, String filename) throws Exception {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(link).openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save
            // error report instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new Exception(connection.getResponseCode() +
                        ": " + connection.getResponseMessage());

            input = connection.getInputStream();
            output = context.openFileOutput(filename, Context.MODE_PRIVATE);

            byte data[] = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }
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

    public static FileInputStream downloadAndOpenFile(Context context, String link, String filename) throws Exception {
        downloadFile(context, link, filename);

        return context.openFileInput(filename);
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();

        return ni != null && ni.isConnected();
    }

    public static int randomCrosswordId(int max) {
        return 1 + (int) (Math.random() * max);
    }

    public static String sizeToString(long size) {
        if (size < 1024)
            return size + " bytes";

        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";

        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

    public static boolean empty(String s) {
        return s == null || s.isEmpty();
    }
}
