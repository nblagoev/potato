package com.nikoblag.android.potato;

import android.app.ActionBar;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.cocosw.undobar.UndoBarController;
import com.cocosw.undobar.UndoBarController.UndoListener;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;
import uk.co.senab.actionbarpulltorefresh.library.viewdelegates.AbsListViewDelegate;
import com.github.kevinsawicki.wishlist.ThrowableLoader;


public class CrosswordActivity extends SherlockActivity
        implements OnRefreshListener, UndoListener, LoaderCallbacks<Void> {

    private PullToRefreshLayout mPullToRefreshLayout;

    private static String[] ITEMS = {
            "", "", "", "3", "4", "5", "6", "7", "", "",
            "", "1", "2", "3", "4", "", "", "7", "", "",
            "", "", "", "", "4", "5", "6", "7", "8", "9",
            "", "", "", "3", "4", "5", "6", "7", "8", "",
            "", "", "", "3", "", "", "", "", "8", "",
            "0", "", "", "3", "", "", "", "", "8", "",
            "0", "1", "2", "3", "4", "", "", "", "8", "",
            "0", "", "", "", "", "", "", "", "8", "",
            "0", "", "", "", "", "", "", "", "8", "",
            "0", "", "", "", "", "", "", "", "8", ""};

    private boolean resumeQueued = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.main_crossword, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            case R.id.settings:
                Intent si = new Intent(this, SettingsActivity.class);
                startActivity(si);
                return true;
            case R.id.hint:
                Toast.makeText(this, "Across: Hint for the selected word\n Down: Hint for the word down", Toast.LENGTH_LONG).show();
                return true;
            case R.id.github:
                Uri uriUrl = Uri.parse("http://github.com/nikoblag/potato");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                this.startActivity(launchBrowser);
                return true;
            case R.id.clear:
                saveState();

                GridView gridView = (GridView) findViewById(R.id.ptr_gridview);
                final int len = gridView.getChildCount();

                for (int i = 0; i < len; i++) {
                    View v = gridView.getChildAt(i);
                    Class c = v.getClass();

                    if (c == EditText.class) {
                        EditText et = (EditText) v;
                        if (!et.getText().toString().isEmpty()) {
                            et.setText("");
                        }
                    }
                }

                Bundle b = new Bundle();
                b.putInt(Const.UNDOBAR_MESSAGESTYLE, Const.UNDOBAR_UNDO);
                UndoBarController.show(this, "Crossword cleared", this, b);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_crossword);

        GridView gridView = (GridView) findViewById(R.id.ptr_gridview);
        gridView.setAdapter(new WordAdapter(this, ITEMS));
        gridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                executeResume();
            }
        });

        mPullToRefreshLayout = (PullToRefreshLayout) findViewById(R.id.ptr_layout);
        ActionBarPullToRefresh.from(this)
                .options(Options.create()
                        .scrollDistance(.75f)
                        .build())
                .allChildrenArePullable()
                .listener(this)
                .useViewDelegate(GridView.class, new AbsListViewDelegate())
                .setup(mPullToRefreshLayout);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onRefreshStarted(final View view) {
        // Note: Android views can't be manipulated outside the thread
        // that was used to create them
        GridView gridView = (GridView) view;
        final int len = gridView.getChildCount();

        for (int i = 0; i < len; i++) {
            View v = gridView.getChildAt(i);
            Class c = v.getClass();

            if (c == EditText.class) {
                EditText et = (EditText) v;
                if (!et.getText().toString().equals(et.getHint())) {
                    et.setBackgroundResource(R.drawable.edit_text_holo_light_invalid);
                } else {
                    et.setBackgroundResource(R.drawable.edit_text_holo_light);
                }
            }
        }

        // Leaving this refresh simulation, because it fixes the UI glitch
        // caused by the fast execution of the above code
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Thread.sleep(Const.SIMULATED_REFRESH_LENGTH);
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


    @Override
    public Loader<Void> onCreateLoader(int id, Bundle args) {
        getSherlock().setProgressBarIndeterminateVisibility(true);

        return new ThrowableLoader<Void>(this, null) {

            @Override
            public Void loadData() throws Exception {
                Thread.sleep(1500);
                throw new Exception("No connection.");
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<Void> loader, Void data) {
        final ThrowableLoader<Void> l = ((ThrowableLoader<Void>) loader);
        if (l.getException() != null) {
            Bundle b = new Bundle();
            b.putInt(Const.UNDOBAR_MESSAGESTYLE, Const.UNDOBAR_RETRY);
            UndoBarController.show(this, l.getException().getMessage(),
                    this, b, false, UndoBarController.RETRYSTYLE);
        } else {
            // something todo if there is no exception
        }
        getSherlock().setProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void onLoaderReset(Loader<Void> loader) {

    }

    @Override
    public void onUndo(Parcelable token) {
        if (token != null) {
            int msgStyle = ((Bundle) token).getInt(Const.UNDOBAR_MESSAGESTYLE);
            switch (msgStyle) {
                case Const.UNDOBAR_UNDO:
                    resumeQueued = true;
                    executeResume();
                    break;
                case Const.UNDOBAR_RETRY:
                    getSherlock().setProgressBarIndeterminateVisibility(false);
                    getLoaderManager().restartLoader(0, null, this);
                    break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        saveState();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Bundle extras = getIntent().getExtras();
        int request =  extras.getInt(Const.ACTIVITY_REQUEST);

        if (request == Const.ACTIVITY_REQUEST_RESUME) {
            resumeQueued = true;
        } else if (request == Const.ACTIVITY_REQUEST_NEW) {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            prefs.edit().clear().commit();
            getLoaderManager().initLoader(0, null, this);
        }
    }

    private void saveState() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().clear();
        editor.putInt("cwd_id", 0); // TODO: put the currently loaded crossword file id

        GridView gridView = (GridView) findViewById(R.id.ptr_gridview);
        final int len = gridView.getChildCount();

        for (int i = 0; i < len; i++) {
            View v = gridView.getChildAt(i);
            Class c = v.getClass();

            if (c == EditText.class) {
                EditText et = (EditText) v;
                String val = et.getText().toString();
                // save the non-empty values only, no need to flood the prefs
                if (!val.isEmpty()) {
                    editor.putString("box_" + i, val);
                }
            }
        }

        editor.commit();
    }

    private void executeResume() {
        if (!resumeQueued)
            return;

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        int cwd_id = prefs.getInt("cwd_id", -1); // TODO: load the crossword file

        GridView gridView = (GridView) findViewById(R.id.ptr_gridview);
        final int len = gridView.getChildCount();

        for (int i = 0; i < len; i++) {
            View v = gridView.getChildAt(i);
            Class c = v.getClass();

            if (c == EditText.class) {
                EditText et = (EditText) v;
                String val = prefs.getString("box_" + i, "");
                if (!val.isEmpty()) {
                    et.setText(val);
                }
            }
        }

        resumeQueued = false;
    }

    private static class WordAdapter extends BaseAdapter {

        private final LayoutInflater mInflater;
        private String[] mItems;

        public WordAdapter(Context context, String[] items) {
            mInflater = LayoutInflater.from(context);
            mItems = items;
        }

        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public String getItem(int position) {
            return mItems[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                String l = getItem(position);

                if (l == null || l.isEmpty()) {
                    convertView = mInflater.inflate(R.layout.simple_space_box, parent, false);
                } else {
                    EditText et = (EditText) mInflater.inflate(R.layout.simple_edit_box, parent, false);
                    et.setId(Util.generateViewId());
                    et.setHint(l);
                    et.setHintTextColor(et.getSolidColor());
                    convertView = et;
                }
            }

            return convertView;
        }
    }
}
