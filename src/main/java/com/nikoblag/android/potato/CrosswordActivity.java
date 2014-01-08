package com.nikoblag.android.potato;

import android.app.ActionBar;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nikoblag.android.potato.fragments.NewCrosswordDialogFragment;
import com.nikoblag.android.potato.util.*;
import com.nikoblag.android.potato.widget.XwBox;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.cocosw.undobar.UndoBarController;
import com.cocosw.undobar.UndoBarController.UndoListener;
import com.cocosw.undobar.UndoBarStyle;
import com.dropbox.sync.android.*;
import com.dropbox.sync.android.DbxTable.QueryResult;
import com.dropbox.sync.android.DbxTable.ResolutionRule;
import com.github.kevinsawicki.wishlist.ThrowableLoader;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;
import uk.co.senab.actionbarpulltorefresh.library.viewdelegates.ScrollYDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.util.*;


public class CrosswordActivity extends SherlockActivity
        implements OnRefreshListener, UndoListener, LoaderCallbacks<XTable> {

    private boolean resumeQueued = false;
    private boolean crosswordCreated = false;
    private int loadedCID = -1;
    private PullToRefreshLayout mPullToRefreshLayout;
    private int penalties = 0;
    private int boxCount = 0;
    private float score = 0;
    private boolean completed = false;
    private XwBox lastFocusedBox;

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
                if (!completed) {
                    NewCrosswordDialogFragment dialog = new NewCrosswordDialogFragment();
                    dialog.setOnConfirmCallback(new NewCrosswordDialogFragment.OnConfirmCallback() {
                        @Override
                        public void onConfirm() {
                            newCrossword();
                        }
                    });
                    dialog.show(getFragmentManager(), "crossword_new_action_confirm");
                } else {
                    newCrossword();
                }

                return true;
            case R.id.settings:
                Intent si = new Intent(this, SettingsActivity.class);
                startActivity(si);
                return true;
            case R.id.hint:
                showHint(getCurrentFocus());
                return true;
            case R.id.github:
                Uri uriUrl = Uri.parse("http://github.com/nikoblag/potato");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                startActivity(launchBrowser);
                return true;
            case R.id.validate:
                validateCrosswordGrid();
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
        validateCrosswordGrid();

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
    public Loader<XTable> onCreateLoader(int id, Bundle args) {
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.logoMark).setVisibility(View.VISIBLE);

        return new ThrowableLoader<XTable>(this, null) {

            @Override
            public XTable loadData() throws Exception {
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
                    loadedCID = Util.randomCrosswordId(getApplicationContext(), max);

                    String cfn = loadedCID + ".jcw";
                    File file = new File(getFilesDir().getPath() + "/" + cfn);

                    if (file.exists())
                        return XTable.generate(new FileInputStream(file));
                }

                // take CPU lock to prevent CPU from going off if the user
                // presses the power button during download
                PowerManager.WakeLock wl = Util.newWakeLock(CrosswordActivity.this);
                wl.acquire();

                FileInputStream jcross;

                try {
                    props.load(Util.downloadAndOpenFile(CrosswordActivity.this, Const.POTATO_METAFILE_URL, ".meta"));
                    max = Integer.parseInt(props.getProperty("max_id"));
                    loadedCID = Util.randomCrosswordId(getApplicationContext(), max);

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

                return XTable.generate(jcross);
            }
        };
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Override
    public void onLoadFinished(Loader<XTable> loader, XTable data) {
        final ThrowableLoader<XTable> l = ((ThrowableLoader<XTable>) loader);
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
    public void onLoaderReset(Loader<XTable> loader) {

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
            DbxAccountManager accMngr = DbxAccountManager.getInstance(getApplicationContext(),
                    Const.DROPBOX_API_KEY, Const.DROPBOX_APP_KEY);

            boolean isCompleted = false;

            if (accMngr.hasLinkedAccount()) {
                try {
                    DbxDatastore dbxDatastore = DbxDatastore.openDefault(accMngr.getLinkedAccount());
                    DbxTable table = dbxDatastore.getTable("scores");
                    DbxRecord last = dbxDatastore.getTable("state").getOrInsert("last");
                    if (last != null && last.hasField("cid"))
                        loadedCID = (int) last.getLong("cid");

                    DbxRecord scoreRecord = table.get("cid-" + loadedCID);

                    isCompleted =  (scoreRecord != null && scoreRecord.getBoolean("completed"));
                    dbxDatastore.close();
                } catch (DbxException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                loadedCID = prefs.getInt("cid", -1);
                SharedPreferences scores = getSharedPreferences("scores", MODE_PRIVATE);
                isCompleted = scores.contains("cid" + loadedCID);
            }

            if (!isCompleted) {
                String cfn = loadedCID + ".jcw";
                File file = new File(getFilesDir().getPath() + "/" + cfn);

                try {
                    createCrossword(XTable.generate(new FileInputStream(file)));
                    resumeQueued = true;
                } catch (Exception ignored) {
                    prefs.edit().clear().commit();
                    getLoaderManager().initLoader(0, null, this);
                }
            } else {
                prefs.edit().clear().commit();
                getLoaderManager().initLoader(0, null, this);
            }
        } else if (request == Const.ACTIVITY_REQUEST_NEW) {
            prefs.edit().clear().commit();
            getLoaderManager().initLoader(0, null, this);
        }
    }

    private void createCrossword(XTable xtable) {
        if (crosswordCreated)
            return;

        boxCount = 0;
        penalties = 0;
        score = 0;
        completed = false;

        LinearLayout grid = (LinearLayout) findViewById(R.id.crosswordGrid);
        LayoutInflater inflater = LayoutInflater.from(this);

        int gridLen = xtable.grid.size();
        for (int i = 0; i < gridLen; i++) {
            List<String> row = xtable.grid.get(i);
            ViewGroup rowView = (ViewGroup) inflater.inflate(R.layout.simple_grid_row, grid, false);
            grid.addView(rowView);
            int rowLen = row.size();
            for (int j = 0; j < rowLen; j++) {
                String hint = row.get(j);
                if (Util.empty(hint)) {
                    rowView.addView(inflater.inflate(R.layout.simple_space_box, rowView, false));
                } else {
                    boxCount++;
                    String defA = null;
                    String defD = null;
                    final XwBox xb = (XwBox) inflater.inflate(R.layout.simple_edit_box, rowView, false);
                    xb.setId(Util.getBoxId(i, j));

                    // now we have to find the definition for the word this box is part of
                    String prevHint;
                    String nextHint = "";
                    String aboveHint;
                    String belowHint = "";

                    prevHint = (j > 0) ? row.get(j - 1) : "";

                    if (Util.empty(prevHint)) {
                        // previous is a space, try the next
                        nextHint = (j < rowLen - 1) ? row.get(j + 1) : "";

                        if (!Util.empty(nextHint)) {
                            String k = findAcrossDefinitionKey(hint, nextHint, row, j + 1, xtable.clues.keySet());
                            defA = xtable.clues.get(k);
                        } // else, next is a space, ignore it; maybe there is a box above/below
                    } else {
                        // previous is a box, use its definition (Across)
                        XwBox box = (XwBox) findViewById(Util.getBoxId(i, j - 1));
                        XTag t = box.getXTag();
                        defA = t.definitionA;
                    }

                    List<String> rowAbove = (i > 0) ? xtable.grid.get(i - 1) : null;
                    aboveHint = (rowAbove != null && j < rowAbove.size()) ? rowAbove.get(j) : "";
                    if (Util.empty(aboveHint)) {
                        // above is a space, try the one below
                        List<String> rowBelow = (i < gridLen - 1) ? xtable.grid.get(i + 1) : null;
                        belowHint = (rowBelow != null && j < rowBelow.size()) ? rowBelow.get(j) : "";

                        if (!Util.empty(belowHint)) {
                            String k = findDownDefinitionKey(hint, belowHint, xtable.grid, i + 1, j, xtable.clues.keySet());
                            defD = xtable.clues.get(k);
                        } // else, the one a below is space, ignore it
                    } else {
                        // above is a box, use it's definition (Down)
                        XwBox box = (XwBox) findViewById(Util.getBoxId(i - 1, j));
                        XTag t = box.getXTag();
                        defD = t.definitionD;
                    }

                    int type = XTag.INNER;

                    if (Util.empty(prevHint) && Util.empty(aboveHint) && !Util.empty(belowHint) && !Util.empty(nextHint)) {
                        xb.setBackgroundResource(R.drawable.edit_text_holo_light_across_down);
                        type = XTag.ACROSS_DOWN;
                    } else if (Util.empty(prevHint) && Util.empty(belowHint) && !Util.empty(nextHint)) {
                        xb.setBackgroundResource(R.drawable.edit_text_holo_light_across);
                        type = XTag.ACROSS;
                    } else if (Util.empty(aboveHint) && !Util.empty(belowHint) && Util.empty(nextHint)) {
                        xb.setBackgroundResource(R.drawable.edit_text_holo_light_down);
                        type = XTag.DOWN;
                    }

                    xb.setTag(new XTag(type, hint, defA, defD));

                    rowView.addView(xb);
                    xb.setOnFocusChangeListener(new OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            if (hasFocus) {
                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(CrosswordActivity.this);
                                if (sp.getBoolean("auto_clue", false))
                                    showHint(v);
                            } else {
                                lastFocusedBox = (XwBox) v;
                            }
                        }
                    });

                    final int m = i;
                    final int n = j;
                    final int M = gridLen;
                    final int N = rowLen;

                    xb.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void afterTextChanged(Editable s) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            if (s.length() == 0)
                                return;

                            focusNextCrosswordBox(xb, n, N, m, M);
                        }
                    });

                    xb.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                                focusNextCrosswordBox(xb, n, N, m, M);
                                return true;
                            }

                            return false;
                        }
                    });

                    xb.addOnBackspaceListener(new XwBox.OnBackspaceListener() {
                        @Override
                        public void onBackspace(XwBox xwb) {
                            focusPrevCrosswordBox(xwb, n, m);
                        }
                    });
                }
            }
        }

        crosswordCreated = true;
    }

    private void focusNextCrosswordBox(XwBox xb, int n, int N, int m, int M) {
        XTag tag = xb.getXTag();
        XwBox box = null;

        try {
            if (!Util.empty(tag.definitionA) && Util.empty(tag.definitionD) && n < N) {
                box = (XwBox) findViewById(Util.getBoxId(m, n + 1));
            } else if (Util.empty(tag.definitionA) && !Util.empty(tag.definitionD) && m < M) {
                box = (XwBox) findViewById(Util.getBoxId(m + 1, n));
            } else {
                XTag lastTag = lastFocusedBox.getXTag();
                if (!Util.empty(lastTag.definitionA) && Util.empty(lastTag.definitionD) && n < N)
                    box = (XwBox) findViewById(Util.getBoxId(m, n + 1));
                else if (Util.empty(lastTag.definitionA) && !Util.empty(lastTag.definitionD) && m < M)
                    box = (XwBox) findViewById(Util.getBoxId(m + 1, n));
                // if nothing else works, check if the box is a start of a word, and go that direction
                else if (tag.type == XTag.ACROSS && n < N)
                    box = (XwBox) findViewById(Util.getBoxId(m, n + 1));
                else if (tag.type == XTag.DOWN && m < M)
                    box = (XwBox) findViewById(Util.getBoxId(m + 1, n));
            }

            if (box != null) {
                box.requestFocus();
                box.selectAll();
            }
        } catch (Exception ignored) {}
    }

    private void focusPrevCrosswordBox(XwBox xb, int n, int m) {
        XTag tag = xb.getXTag();
        XwBox box = null;

        try {
            if (!Util.empty(tag.definitionA) && Util.empty(tag.definitionD) && n > 0) {
                box = (XwBox) findViewById(Util.getBoxId(m, n - 1));
            } else if (Util.empty(tag.definitionA) && !Util.empty(tag.definitionD) && m > 0) {
                box = (XwBox) findViewById(Util.getBoxId(m - 1, n));
            } else {
                XTag lastTag = lastFocusedBox.getXTag();
                if (!Util.empty(lastTag.definitionA) && Util.empty(lastTag.definitionD) && n > 0)
                    box = (XwBox) findViewById(Util.getBoxId(m, n - 1));
                else if (Util.empty(lastTag.definitionA) && !Util.empty(lastTag.definitionD) && m > 0)
                    box = (XwBox) findViewById(Util.getBoxId(m - 1, n));
            }

            if (box != null) {
                box.requestFocus();
                box.selectAll();
            }
        } catch (Exception ignored) {}
    }

    private String findDownDefinitionKey(String currHint, String nextHint, List<List<String>> grid,
                                         int i, int j, Set<String> keys) {
        String wordPart = currHint + nextHint;
        Set<String> found = new HashSet<String>();

        for (String k : keys) {
            if (k.startsWith(wordPart))
                found.add(k);
        }

        if (found.size() == 1)
            return found.iterator().next();
        else if (found.isEmpty() || i >= grid.size())
            return null;
        else
            return findDownDefinitionKey(wordPart, grid.get(i + 1).get(j), grid, i + 1, j, found);
    }

    private String findAcrossDefinitionKey(String currHint, String nextHint,
                                           List<String> row, int j, Set<String> keys) {
        String wordPart = currHint + nextHint;
        Set<String> found = new HashSet<String>();

        for (String k : keys) {
            if (k.startsWith(wordPart))
                found.add(k);
        }

        if (found.size() == 1)
            return found.iterator().next();
        else if (found.isEmpty() || j >= row.size())
            return null;
        else
            return findAcrossDefinitionKey(wordPart, row.get(j + 1), row, j + 1, found);
    }

    private void showHint(View focusedView) {
        XwBox box = (XwBox) focusedView;
        if (box != null) {
            XTag tag = box.getXTag();
            String msg = "";

            if (!Util.empty(tag.definitionA))
                msg += "> " + tag.definitionA;

            if (!Util.empty(tag.definitionD))
                msg += (msg.length() > 0 ? "\n\n" : "") + "v " + tag.definitionD;

            Bundle b = new Bundle();
            b.putInt(Const.UNDOBAR_MESSAGESTYLE, Const.UNDOBAR_HINT);
            UndoBarController.show(this, msg, this, b, false,
                    new UndoBarStyle(R.drawable.ic_action_cancel, R.string.BLANK, -1));

        } else {
            Toast.makeText(this, "For hints, select a text box", Toast.LENGTH_SHORT).show();
        }
    }

    private void loopOverCrossword(CrosswordLoopFunction<XwBox, Integer, Integer> func) {
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

                    if (c2 == XwBox.class)
                        func.execute((XwBox) v2, i, j);
                }
            }
        }
    }

    private void newCrossword() {
        ((ViewGroup) findViewById(R.id.crosswordGrid)).removeAllViews();
        crosswordCreated = false;
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.logoMark).setVisibility(View.VISIBLE);
        getLoaderManager().restartLoader(0, null, this);
    }

    private void clearCrossword() {
        loopOverCrossword(new CrosswordLoopFunction<XwBox, Integer, Integer>() {
            @Override
            public void execute(XwBox xb, Integer row, Integer col) {
                if (!xb.getText().toString().isEmpty())
                    xb.setText("");

                setCrosswordBoxBackground(xb, false);
            }
        });
    }

    private void validateCrosswordGrid() {
        int penaltiesBefore = penalties;

        loopOverCrossword(new CrosswordLoopFunction<XwBox, Integer, Integer>() {
            @Override
            public void execute(XwBox xb, Integer row, Integer col) {
                XTag tag = xb.getXTag();

                if (!xb.getText().toString().toLowerCase().equals(tag.answer.toLowerCase())) {
                    penalties++;
                    setCrosswordBoxBackground(xb, true);
                } else {
                    setCrosswordBoxBackground(xb, false);
                }
            }
        });

        score = ((float)(boxCount - penalties)) / boxCount * 100;
        String ss = String.format("%.2f", ((score < 0) ? 0 : score));
        Toast.makeText(this, "Validation complete\nScore: " + ss, Toast.LENGTH_SHORT).show();

        if (penaltiesBefore == penalties) {
            completed = true;
            saveState();
        } else {
            Util.vibrate(this, 200);
        }
    }

    private void setCrosswordBoxBackground(XwBox xb, boolean invalid) {
        XTag tag = xb.getXTag();

        if (invalid) {
            if (tag.type == XTag.ACROSS_DOWN)
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_invalid_across_down);
            else if (tag.type == XTag.ACROSS)
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_invalid_across);
            else if (tag.type == XTag.DOWN)
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_invalid_down);
            else
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_invalid);
        } else {
            if (tag.type == XTag.ACROSS_DOWN)
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_across_down);
            else if (tag.type == XTag.ACROSS)
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_across);
            else if (tag.type == XTag.DOWN)
                xb.setBackgroundResource(R.drawable.edit_text_holo_light_down);
            else
                xb.setBackgroundResource(R.drawable.edit_text_holo_light);
        }
    }

    private void saveState() {
        DbxAccountManager accMngr = DbxAccountManager.getInstance(getApplicationContext(),
                Const.DROPBOX_API_KEY, Const.DROPBOX_APP_KEY);

        if (accMngr.hasLinkedAccount()) {
            try {
                DbxDatastore dbxDatastore = DbxDatastore.openDefault(accMngr.getLinkedAccount());
                DbxTable scoreTable = dbxDatastore.getTable("scores");

                scoreTable.setResolutionRule("score", ResolutionRule.MAX);
                DbxRecord scoreRecord = scoreTable.getOrInsert("cid-" + loadedCID);

                if (!scoreRecord.hasField("completed") || !scoreRecord.getBoolean("completed")) {
                    scoreRecord.set("score", score).set("completed", completed)
                               .set("date", new Date()).set("penalties", penalties);

                    if (!completed) {
                        DbxFields q = new DbxFields().set("type", "box").set("active", true);
                        final DbxTable stateTable = dbxDatastore.getTable("state");

                        for (DbxRecord stateRecord : stateTable.query(q)) {
                            stateRecord.set("active", false);
                        }

                        loopOverCrossword(new CrosswordLoopFunction<XwBox, Integer, Integer>() {
                            @Override
                            public void execute(XwBox xb, Integer row, Integer col) {
                                String val = xb.getText().toString();
                                // save the non-empty values only, no need to flood the prefs
                                if (!val.isEmpty()) {
                                    try {
                                        DbxFields query = new DbxFields().set("type", "box").set("row", row).set("column", col);
                                        DbxFields fields = new DbxFields().set("value", val).set("active", true);
                                        QueryResult r = stateTable.query(query);

                                        if (r.count() < 1)
                                            stateTable.insert(query).setAll(fields);
                                        else
                                            r.iterator().next().setAll(fields);
                                    } catch (DbxException e) {
                                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        });

                        stateTable.getOrInsert("last").set("cid", loadedCID);
                    }

                    dbxDatastore.sync();
                }

                dbxDatastore.close();
            } catch (DbxException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            if (!completed) {
                final SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);
                final SharedPreferences.Editor editor = prefs.edit().clear();
                editor.putInt("cid", loadedCID);
                editor.putFloat("score_cid" + loadedCID, score);
                editor.putInt("penalties_cid" + loadedCID, penalties);

                loopOverCrossword(new CrosswordLoopFunction<XwBox, Integer, Integer>() {
                    @Override
                    public void execute(XwBox xb, Integer row, Integer col) {
                        String val = xb.getText().toString();
                        // save the non-empty values only, no need to flood the prefs
                        if (!val.isEmpty())
                            editor.putString("box_" + row + "_" + col, val);
                    }
                });

                editor.commit();
            } else {
                final SharedPreferences prefs = getSharedPreferences("scores", MODE_PRIVATE);
                final SharedPreferences.Editor editor = prefs.edit().clear();
                editor.putFloat("cid" + loadedCID, score);
                editor.commit();
            }
        }
    }

    private void executeResume() {
        if (!resumeQueued)
            return;

        DbxAccountManager accMngr = DbxAccountManager.getInstance(getApplicationContext(),
                Const.DROPBOX_API_KEY, Const.DROPBOX_APP_KEY);

        if (accMngr.hasLinkedAccount()) {
            try {
                DbxDatastore dbxDatastore = DbxDatastore.openDefault(accMngr.getLinkedAccount());
                DbxTable scoreTable = dbxDatastore.getTable("scores");

                DbxRecord scoreRecord = scoreTable.get("cid-" + loadedCID);

                if (scoreRecord != null && !scoreRecord.getBoolean("completed")) {
                    score = (float) scoreRecord.getDouble("score");
                    penalties = (int) scoreRecord.getLong("penalties");

                    final DbxFields q = new DbxFields().set("type", "box").set("active", true);
                    final DbxTable stateTable = dbxDatastore.getTable("state");

                    loopOverCrossword(new CrosswordLoopFunction<XwBox, Integer, Integer>() {
                        @Override
                        public void execute(XwBox xb, Integer row, Integer col) {
                            try {
                                q.set("row", row).set("column", col);
                                QueryResult r = stateTable.query(q);

                                if (r.count() > 0) {
                                    String val = r.iterator().next().getString("value");

                                    if (val != null && !val.isEmpty())
                                        xb.setText(val);
                                }
                            } catch (DbxException e) {
                                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                    dbxDatastore.sync();
                }

                dbxDatastore.close();
            } catch (DbxException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            final SharedPreferences prefs = getSharedPreferences("resume", MODE_PRIVATE);

            score = prefs.getFloat("score_cid" + loadedCID, 0);
            penalties = prefs.getInt("penalties_cid" + loadedCID, 0);

            loopOverCrossword(new CrosswordLoopFunction<XwBox, Integer, Integer>() {
                @Override
                public void execute(XwBox xb, Integer row, Integer col) {
                    String val = prefs.getString("box_" + row + "_" + col, "");
                    if (!val.isEmpty())
                        xb.setText(val);
                }
            });
        }

        resumeQueued = false;
    }

}
