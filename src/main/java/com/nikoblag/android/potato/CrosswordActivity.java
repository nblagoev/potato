package com.nikoblag.android.potato;

import android.app.ActionBar;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.nikoblag.android.potato.util.Const;
import com.nikoblag.android.potato.util.CrosswordLoopFunction;
import com.nikoblag.android.potato.util.Util;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.cocosw.undobar.UndoBarController;
import com.cocosw.undobar.UndoBarController.UndoListener;
import com.github.kevinsawicki.wishlist.ThrowableLoader;
import org.xmlpull.v1.XmlPullParserException;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;
import uk.co.senab.actionbarpulltorefresh.library.viewdelegates.ScrollYDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;


public class CrosswordActivity extends SherlockActivity
        implements OnRefreshListener, UndoListener, LoaderCallbacks<List<List<String>>> {

    private boolean resumeQueued = false;
    private boolean crosswordCreated = false;
    private int loadedCID = -1;
    private PullToRefreshLayout mPullToRefreshLayout;

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
            case R.id.new_action:
                ((ViewGroup) findViewById(R.id.crosswordGrid)).removeAllViews();
                crosswordCreated = false;
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                findViewById(R.id.logoMark).setVisibility(View.VISIBLE);
                getLoaderManager().restartLoader(0, null, this);
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
                startActivity(launchBrowser);
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

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        if (!sharedPref.getBoolean("auto_resume", true)) {
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
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
    public Loader<List<List<String>>> onCreateLoader(int id, Bundle args) {
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.logoMark).setVisibility(View.VISIBLE);

        return new ThrowableLoader<List<List<String>>>(this, null) {

            @Override
            public List<List<String>> loadData() throws Exception {
                Properties props = new Properties();
                File meta = new File(getFilesDir().getPath() + "/.meta");
                int max;

                // check for internet connection, if not available try to
                // parse the cached files (if any)
                if (!Util.isNetworkAvailable(CrosswordActivity.this) && meta.exists()) {
                    props.load(new FileInputStream(meta));
                    max = Integer.parseInt(props.getProperty("max_id"));
                    // should we set the loadedCID after the crossword was
                    // successfully rendered?
                    loadedCID = Util.randomCrosswordId(max);

                    String cfn = loadedCID + ".jcw";
                    File file = new File(getFilesDir().getPath() + "/" + cfn);

                    if (file.exists())
                        return Util.XTable.generate(new FileInputStream(file));
                }

                // take CPU lock to prevent CPU from going off if the user
                // presses the power button during download
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
                wl.acquire();

                FileInputStream jcross;

                try {
                    props.load(Util.downloadAndOpenFile(CrosswordActivity.this, Const.POTATO_METAFILE_URL, ".meta"));
                    max = Integer.parseInt(props.getProperty("max_id"));
                    loadedCID = Util.randomCrosswordId(max);

                    String cfn = loadedCID + ".jcw";
                    File file = new File(getFilesDir().getPath() + "/" + cfn);
                    if (file.exists())
                        jcross = new FileInputStream(file);
                    else
                        jcross = Util.downloadAndOpenFile(CrosswordActivity.this, Const.POTATO_DROPBOX_URL + cfn, cfn);
                } catch (UnknownHostException e) {
                    throw new Exception("No connection.");
                } finally {
                    wl.release();
                }

                return Util.XTable.generate(jcross);
            }
        };
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Override
    public void onLoadFinished(Loader<List<List<String>>> loader, List<List<String>> data) {
        final ThrowableLoader<List<List<String>>> l = ((ThrowableLoader<List<List<String>>>) loader);
        if (l.getException() != null) {
            Bundle b = new Bundle();
            b.putInt(Const.UNDOBAR_MESSAGESTYLE, Const.UNDOBAR_RETRY);
            UndoBarController.show(this, l.getException().getMessage(),
                    this, b, false, UndoBarController.RETRYSTYLE);
        } else {
            createCrossword(data);
            findViewById(R.id.logoMark).setVisibility(View.GONE);
        }

        findViewById(R.id.progressBar).setVisibility(View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<List<List<String>>> loader) {

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
                    findViewById(R.id.logoMark).setVisibility(View.VISIBLE);
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
        int request = extras.getInt(Const.ACTIVITY_REQUEST);
        SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);

        if (request == Const.ACTIVITY_REQUEST_RESUME) {
            loadedCID = prefs.getInt("cid", -1);

            String cfn = loadedCID + ".jcw";
            File file = new File(getFilesDir().getPath() + "/" + cfn);

            try {
                createCrossword(Util.XTable.generate(new FileInputStream(file)));
                resumeQueued = true;
            } catch (Exception ignored) {
                prefs.edit().clear().commit();
                getLoaderManager().initLoader(0, null, this);
            }
        } else if (request == Const.ACTIVITY_REQUEST_NEW) {
            prefs.edit().clear().commit();
            getLoaderManager().initLoader(0, null, this);
        }
    }

    private void createCrossword(List<List<String>> xtable) {
        if (crosswordCreated)
            return;

        LinearLayout grid = (LinearLayout) findViewById(R.id.crosswordGrid);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (List<String> row : xtable) {
            ViewGroup rowView = (ViewGroup) inflater.inflate(R.layout.simple_grid_row, grid, false);
            grid.addView(rowView);
            for (String hint : row) {
                if (hint == null || hint.isEmpty()) {
                    rowView.addView(inflater.inflate(R.layout.simple_space_box, rowView, false));
                } else {
                    EditText et = (EditText) inflater.inflate(R.layout.simple_edit_box, rowView, false);
                    et.setId(Util.generateViewId());
                    et.setHint(hint);
                    et.setHintTextColor(et.getSolidColor());
                    rowView.addView(et);
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

                    if (c2 == EditText.class)
                        func.execute((EditText) v2, i, j);
                }
            }
        }
    }

    private void clearCrossword() {
        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                if (!et.getText().toString().isEmpty())
                    et.setText("");
            }
        });
    }

    private void validateCrosswordGrid(boolean announce) {
        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                if (!et.getText().toString().equals(et.getHint()))
                    et.setBackgroundResource(R.drawable.edit_text_holo_light_invalid);
                else
                    et.setBackgroundResource(R.drawable.edit_text_holo_light);
            }
        });

        if (announce)
            Toast.makeText(this, "Validation complete", Toast.LENGTH_SHORT).show();
    }

    private void saveState() {
        final SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit().clear();
        editor.putInt("cid", loadedCID);

        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                String val = et.getText().toString();
                // save the non-empty values only, no need to flood the prefs
                if (!val.isEmpty())
                    editor.putString("box_" + row + "_" + col, val);
            }
        });

        editor.commit();
    }

    private void executeResume() {
        if (!resumeQueued)
            return;

        final SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);
        loopOverCrossword(new CrosswordLoopFunction<EditText, Integer, Integer>() {
            @Override
            public void execute(EditText et, Integer row, Integer col) {
                String val = prefs.getString("box_" + row + "_" + col, "");
                if (!val.isEmpty())
                    et.setText(val);
            }
        });

        resumeQueued = false;
    }

}
