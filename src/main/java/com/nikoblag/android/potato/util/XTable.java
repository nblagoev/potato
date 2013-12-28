package com.nikoblag.android.potato.util;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XTable {
    public final List<List<String>> grid;
    public final Map<String, String> clues;

    public XTable(List<List<String>> grid, Map<String, String> clues) {
        this.clues = clues;
        this.grid = grid;
    }

    public static XTable generate(InputStream jcross) throws XmlPullParserException, IOException {
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

    private static XTable readData(XmlPullParser parser) throws XmlPullParserException, IOException {
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

    private static XTable readCrossword(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "crossword");
        List<List<String>> grid = null;
        Map<String, String> clues = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;

            String name = parser.getName();
            if (name.equals("grid"))
                grid = readGrid(parser);
            else if (name.equals("clues"))
                clues = readClues(parser);
            else
                skip(parser);
        }
        return new XTable(grid, clues);
    }

    private static List<List<String>> readGrid(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "grid");
        List<List<String>> rows = new ArrayList<List<String>>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;

            String name = parser.getName();
            if (name.equals("row")) {
                List<String> row = readRow(parser);
                for (int i = row.size(); --i >= 0 && row.get(i).isEmpty();) {
                    row.remove(i);
                }

                rows.add(row);
            } else {
                skip(parser);
            }
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

    private static Map<String, String> readClues(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "clues");
        Map<String, String> items = new HashMap<String, String>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;

            String name = parser.getName();
            if (name.equals("item")) {
                Clue clue = readItem(parser);
                items.put(clue.word, clue.def);
            } else {
                skip(parser);
            }
        }
        return items;
    }

    private static Clue readItem(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "item");
        Clue clue = new Clue();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;

            String name = parser.getName();
            if (name.equals("word"))
                clue.word = readText(parser);
            else if (name.equals("def"))
                clue.def = readText(parser);
            else
                skip(parser);
        }
        return clue;
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

    private static class Clue {
        public String word;
        public String def;
    }
}
