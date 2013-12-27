package com.nikoblag.android.potato.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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

    public static class XTable {
        public static List<List<String>> generate(InputStream jcross) throws XmlPullParserException, IOException {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(jcross, null);
                parser.nextTag();

                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG)
                        continue;

                    String name = parser.getName();
                    if (name.equals("data"))
                        return readData(parser);
                    else
                        skip(parser);
                }
            } finally {
                jcross.close();
            }

            return null;
        }

        private static List<List<String>> readData(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "data");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;

                String name = parser.getName();
                if (name.equals("crossword"))
                    return readCrossword(parser);
                else
                    skip(parser);
            }
            return null;
        }

        private static List<List<String>> readCrossword(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "crossword");
            List<List<String>> grid = null;
            //String[] clues = null;
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;

                String name = parser.getName();
                if (name.equals("grid"))
                    grid = readGrid(parser);
                //} else if (name.equals("clues")) {
                    //clues = readClues(parser);
                else
                    skip(parser);
            }
            return grid;
        }

        private static List<List<String>> readGrid(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "grid");
            List<List<String>> rows = new ArrayList<List<String>>();
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;

                String name = parser.getName();
                if (name.equals("row"))
                    rows.add(readRow(parser));
                else
                    skip(parser);
            }
            return rows;
        }

        private static List<String> readRow(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "row");
            List<String> row = new ArrayList<String>();
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;

                String name = parser.getName();
                if (name.equals("cell"))
                    row.add(readText(parser));
                else
                    skip(parser);
            }
            return row;
        }

        private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
            String result = "";
            if (parser.next() == XmlPullParser.TEXT) {
                result = parser.getText();
                parser.nextTag();
            }

            return result;
        }


        private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                throw new IllegalStateException();

            int depth = 1;
            while (depth != 0) {
                switch (parser.next()) {
                    case XmlPullParser.END_TAG:
                        depth--;
                        break;
                    case XmlPullParser.START_TAG:
                        depth++;
                        break;
                }
            }
        }
    }
}
