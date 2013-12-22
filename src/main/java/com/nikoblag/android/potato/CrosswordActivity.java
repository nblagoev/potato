package com.nikoblag.android.potato;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;
import uk.co.senab.actionbarpulltorefresh.library.viewdelegates.AbsListViewDelegate;

import java.util.ArrayList;

public class CrosswordActivity extends SherlockActivity
        implements OnRefreshListener {

    private PullToRefreshLayout mPullToRefreshLayout;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.main_crossword, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
           case R.id.github:
                Uri uriUrl = Uri.parse("http://github.com/nikoblag/potato");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                this.startActivity(launchBrowser);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crossword);

        GridView gridView = (GridView) findViewById(R.id.ptr_gridview);
        gridView.setAdapter(new WordAdapter(this));

        mPullToRefreshLayout = (PullToRefreshLayout) findViewById(R.id.ptr_layout);
        ActionBarPullToRefresh.from(this)
                .options(Options.create()
                        .scrollDistance(.75f)
                        .build())
                .allChildrenArePullable()
                .listener(this)
                .useViewDelegate(GridView.class, new AbsListViewDelegate())
                .setup(mPullToRefreshLayout);
    }

    @Override
    public void onRefreshStarted(View view) {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Thread.sleep(Constants.SIMULATED_REFRESH_LENGTH);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);

                mPullToRefreshLayout.setRefreshComplete();
            }
        }.execute();
    }


    private static class WordAdapter extends BaseAdapter {

        private static String[] ITEMS = {
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "0", "" , "", "" , "", "5" , "" , "" , "" , "" ,
                "0", "" , "", "" , "", "5" , "" , "" , "" , "" ,
                "0", "" , "", "" , "", "5" , "6" , "7" , "8" , "9" ,
                "0", "" , "", "" , "", "" , "" , "7" , "" , "" ,
                "0", "" , "2", "" , "", "" , "" , "" , "" , "" ,
                "0", "" , "2", "" , "", "" , "" , "" , "" , "" ,
                "0", "" , "2", "" , "", "" , "" , "" , "" , "" };

        private final LayoutInflater mInflater;

        public WordAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return ITEMS.length;
        }

        @Override
        public String getItem(int position) {
            return ITEMS[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            EditText et = (EditText) convertView;
            if (et == null) {
                et = (EditText) mInflater.inflate(R.layout.simple_edit_box, parent, false);

                String l = getItem(position);

                if (l == null || l.isEmpty()) {
                    et.setVisibility(View.INVISIBLE);
                } else {
                    et.setHint(l);
                    et.setHintTextColor(et.getSolidColor());
                }
            }
            return et;
        }
    }
}
