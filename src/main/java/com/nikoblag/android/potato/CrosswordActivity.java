package com.nikoblag.android.potato;

import android.app.ActionBar;
import android.app.LoaderManager.LoaderCallbacks;
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
import com.cocosw.undobar.UndoBarController;
import com.cocosw.undobar.UndoBarController.UndoListener;
import com.nikoblag.android.potato.util.Const;
import com.nikoblag.android.potato.util.CrosswordLoopFunction;
import com.nikoblag.android.potato.util.Util;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;
import uk.co.senab.actionbarpulltorefresh.library.viewdelegates.AbsListViewDelegate;
import com.github.kevinsawicki.wishlist.ThrowableLoader;
import uk.co.senab.actionbarpulltorefresh.library.viewdelegates.ScrollYDelegate;


public class CrosswordActivity extends SherlockActivity
        implements OnRefreshListener, UndoListener, LoaderCallbacks<Void> {

    private PullToRefreshLayout mPullToRefreshLayout;

    private static String[][] ITEMS = {
            {"", "", "", "3", "4", "5", "6", "7", "", ""},
            {"", "1", "2", "3", "4", "", "", "7", "", ""},
            {"", "", "", "", "4", "5", "6", "7", "8", "9"},
            {"", "", "", "3", "4", "5", "6", "7", "8", ""},
            {"", "", "", "3", "", "", "", "", "8", ""},
            {"0", "", "", "3", "", "", "", "", "8", ""},
            {"0", "1", "2", "3", "4", "", "", "", "8", ""},
            {"0", "", "", "", "", "", "", "", "8", ""},
            {"0", "", "", "", "", "", "", "", "8", ""},
            {"0", "", "", "", "", "", "", "", "8", ""},
            {"", "", "", "", "", "", "6", "7", "8", "9"},
            {"", "", "", "", "", "", "", "", "8", ""},
            {"", "", "", "", "", "", "", "", "8", ""}};

    private boolean resumeQueued = false;
    private boolean crosswordCreated = false;

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
            case R.id.validate:
                validateCrosswordGrid(true);
                return true;
            case R.id.clear:
                saveState();

                clearCrossword();

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
        setContentView(R.layout.activity_crossword);

        findViewById(R.id.crosswordGrid)
                .getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
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
                .useViewDelegate(ScrollView.class, new ScrollYDelegate())
                .setup(mPullToRefreshLayout);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onRefreshStarted(View view) {
        // Note: Android views can't be manipulated outside the thread
        // that was used to create them
        validateCrosswordGrid(false);

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
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

        return new ThrowableLoader<Void>(this, null) {

            @Override
            public Void loadData() throws Exception {
                Thread.sleep(1500);
                //throw new Exception("No connection.");
                return null;
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
            createCrossword();
        }

        findViewById(R.id.progressBar).setVisibility(View.GONE);
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
                    findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
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
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        if (request == Const.ACTIVITY_REQUEST_RESUME) {
            int cwd_id = prefs.getInt("cwd_id", -1); // TODO: load the crossword file

            // Simulate a crossword file rendering
            createCrossword();
            resumeQueued = true;
        } else if (request == Const.ACTIVITY_REQUEST_NEW) {
            prefs.edit().clear().commit();
            getLoaderManager().initLoader(0, null, this);
        }
    }

    private void createCrossword() {
        if (crosswordCreated)
            return;

        LinearLayout grid = (LinearLayout) findViewById(R.id.crosswordGrid);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < ITEMS.length; i++) {
            ViewGroup row = (ViewGroup) inflater.inflate(R.layout.simple_grid_row, grid, false);
            grid.addView(row);
            for (int j = 0; j < ITEMS[i].length; j++) {
                String l = ITEMS[i][j];

                if (l == null || l.isEmpty()) {
                    row.addView(inflater.inflate(R.layout.simple_space_box, row, false));
                } else {
                    EditText et = (EditText) inflater.inflate(R.layout.simple_edit_box, row, false);
                    et.setId(Util.generateViewId());
                    et.setHint(l);
                    et.setHintTextColor(et.getSolidColor());
                    row.addView(et);
                }
            }
        }

        crosswordCreated = true;
    }

    private void loopOverCrossword(CrosswordLoopFunction<EditText, Integer, Integer> func) {
        LinearLayout grid = (LinearLayout) findViewById(R.id.crosswordGrid);
        int len = grid.getChildCount();

        for (int i = 0; i < len; i++) {
            View v = grid.getChildAt(i);
            Class c = v.getClass();

            if (c == LinearLayout.class) {
                LinearLayout row = (LinearLayout) v;
                int row_len = row.getChildCount();

                for (int j = 0; j < row_len; j++) {
                    View v2 = row.getChildAt(j);
                    Class c2 = v2.getClass();

                    if (c2 == EditText.class) {
                        EditText et = (EditText) v2;
                        func.execute(et, i, j);
                    }
                }
            }
        }
    }

    private void clearCrossword() {
        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                if (!et.getText().toString().isEmpty()) {
                    et.setText("");
                }
            }
        });
    }

    private void validateCrosswordGrid(boolean announce) {
        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                if (!et.getText().toString().equals(et.getHint())) {
                    et.setBackgroundResource(R.drawable.edit_text_holo_light_invalid);
                } else {
                    et.setBackgroundResource(R.drawable.edit_text_holo_light);
                }
            }
        });

        if (announce)
            Toast.makeText(this, "Validation complete", Toast.LENGTH_SHORT).show();
    }

    private void saveState() {
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit().clear();
        editor.putInt("cwd_id", 0); // TODO: put the currently loaded crossword file id

        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                String val = et.getText().toString();
                // save the non-empty values only, no need to flood the prefs
                if (!val.isEmpty()) {
                    editor.putString("box_" + row + "_" + col, val);
                }
            }
        });

        editor.commit();
    }

    private void executeResume() {
        if (!resumeQueued)
            return;

        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                String val = prefs.getString("box_" + row + "_" + col, "");
                if (!val.isEmpty()) {
                    et.setText(val);
                }
            }
        });

        resumeQueued = false;
    }

}
