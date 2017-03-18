package pw.phylame.crawling.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jem.Attributes;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;
import jem.epm.util.ParserException;
import jem.util.Variants;
import lombok.val;
import pw.phylame.commons.io.IOUtils;
import pw.phylame.commons.util.CollectionUtils;
import pw.phylame.commons.util.StringUtils;
import pw.phylame.crawling.R;
import pw.phylame.crawling.model.ITask;
import pw.phylame.crawling.util.GlideListenerAdapter;
import pw.phylame.crawling.util.Jem;
import pw.phylame.support.DataHub;
import pw.phylame.support.Views;
import pw.phylame.support.Worker;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static pw.phylame.support.Views.hideIME;
import static pw.phylame.support.Views.viewById;

public class TaskActivity extends BaseActivity implements View.OnClickListener {
    public static final int TASK_KEY = 100;

    private static final String HISTORY_NAME = "url-history";
    private static final String ENCODING = "UTF-16LE";

    private AutoCompleteTextView mURL;
    private Spinner mFormat;
    private TextView mPath;

    private View mProgress;

    private View mPlaceholder;
    private View mOverview;
    private TextView mName;
    private TextView mInfo;
    private TextView mIntro;
    private ImageView mCover;
    private CheckBox mBackup;

    private List<String> mHistories;
    private ArrayAdapter<String> mAdapter;

    private ITask mTask;
    private File mThumbnail;
    private CrawlerBook mBook;
    private CrawlerConfig mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);
        setupCenteredToolbar(R.id.toolbar, true);
        setupColoredStatus();
        setTitle(R.string.activity_new_task_title);

        mTask = DataHub.get(TASK_KEY);
        if (mTask == null) {
            Log.e(TAG, "no task specified");
            finish();
        }

        loadViews();

        val prefs = getSharedPreferences("general", MODE_PRIVATE);

        mConfig = new CrawlerConfig();
        mConfig.timeout = prefs.getInt("crawler.timeout", mConfig.timeout);
        mConfig.tryCount = prefs.getInt("crawler.tryCount", mConfig.tryCount);

        loadHistory();
        mURL.setThreshold(prefs.getInt("url.threshold", 4));

        mPath.setText(prefs.getString("out.path", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()));
        mBackup.setChecked(prefs.getBoolean("out.backup", false));

        loadFormats(prefs);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.view:
                fetchBook();
                break;
        }
    }

    private void loadViews() {
        mURL = viewById(this, R.id.url);
        findViewById(R.id.view).setOnClickListener(this);
        mFormat = viewById(this, R.id.formats);
        mPath = viewById(this, R.id.path);
        findViewById(R.id.path_bar).setOnClickListener(v -> {
            // TODO: 2017-3-2 select output directory
        });
        mPlaceholder = viewById(this, R.id.placeholder);
        mOverview = viewById(this, R.id.overview);
        mInfo = viewById(this, R.id.info);
        mIntro = viewById(this, R.id.intro);
        mCover = viewById(this, R.id.cover);
        mCover.setOnClickListener(v -> {
            if (mThumbnail == null) {
                return;
            }
            val intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(mThumbnail), "image/*");
            startActivity(intent);
        });
        mName = viewById(this, R.id.name);
        mBackup = viewById(this, R.id.backup);
        mProgress = viewById(this, R.id.progress);
    }

    private void loadHistory() {
        mHistories = new ArrayList<>();
        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mHistories);
        mURL.setAdapter(mAdapter);
        mURL.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                fetchBook();
            }
            return false;
        });

        // load history from file
        Worker.execute(() -> {
            val file = new File(getFilesDir(), HISTORY_NAME);
            if (file.exists()) {
                return IOUtils.toLines(file, ENCODING, true);
            } else {
                return null;
            }
        }, urls -> mAdapter.addAll(urls));
    }

    private void loadFormats(SharedPreferences prefs) {
        Worker.execute(() -> {
            Jem.init();
            return CollectionUtils.listOf(EpmManager.supportedMakers());
        }, names -> {
            val adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mFormat.setAdapter(adapter);
            if (!names.isEmpty()) {
                mFormat.post(() -> {
                    val position = names.indexOf(prefs.getString("out.format", "epub"));
                    mFormat.setSelection(position < 0 ? 0 : position);
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("format", mFormat.getSelectedItemPosition());
        outState.putBoolean("placeholder", mPlaceholder.getVisibility() == View.VISIBLE);
        if (mThumbnail != null) {
            outState.putString("thumbnail", mThumbnail.getPath());
            outState.putParcelable("cover", ((BitmapDrawable) mCover.getDrawable()).getBitmap());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mFormat.post(() -> mFormat.setSelection(savedInstanceState.getInt("format")));
        val visibility = savedInstanceState.getBoolean("placeholder", true);
        if (!visibility) {
            mOverview.setVisibility(View.VISIBLE);
            mPlaceholder.setVisibility(View.GONE);
        } else {
            mOverview.setVisibility(View.GONE);
            mPlaceholder.setVisibility(View.VISIBLE);
        }
        val bmp = savedInstanceState.getParcelable("cover");
        if (bmp != null) {
            mCover.setImageBitmap((Bitmap) bmp);
            mThumbnail = new File(savedInstanceState.getString("thumbnail"));
        } else if (!visibility) {
            mCover.setImageResource(R.mipmap.ic_book);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mHistories.isEmpty()) {
            val file = new File(getFilesDir(), HISTORY_NAME);
            try {
                IOUtils.writeLines(file, mHistories, "\n", ENCODING);
            } catch (IOException e) {
                Log.e(TAG, "cannot save histories to file: " + file, e);
            }
        }
        Worker.execute(this::cleanupBook);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_new, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_done:
                onDone();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void cleanupBook() {
        if (mBook != null && !mTask.isInitialized()) {
            mBook.cleanup();
        }
        mBook = null;
    }

    private boolean isValidURL(String url) {
        if (url.isEmpty()) {
            return false;
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }

    private void onDone() {
        val url = mURL.getText().toString();
        if (!isValidURL(url)) {
            Toast.makeText(this, R.string.new_task_invalid_url, Toast.LENGTH_SHORT).show();
            mURL.requestFocus();
            return;
        }
        val format = mFormat.getSelectedItem().toString();
        val output = mPath.getText().toString();
        if (output.isEmpty()) {
            Toast.makeText(this, R.string.new_task_invalid_output, Toast.LENGTH_SHORT).show();
            return;
        }
        val backup = mBackup.isChecked();

        val prefs = getSharedPreferences("general", MODE_PRIVATE);
        val keepFormat = prefs.getBoolean("task.keepFormat", false);
        val keepOutput = prefs.getBoolean("task.keepOutput", false);
        val editor = prefs.edit();
        if (keepFormat) { // use last format
            editor.putString("out.format", format);
        }
        if (keepOutput) { // use last output
            editor.putString("out.output", output);
        }
        editor.apply();

        if (mBook != null) {
            mTask.init(mBook, new File(output), format, backup);
        } else {
            mTask.init(url, new File(output), format, backup);
        }

        setResult(RESULT_OK);
        addHistory(url);
        finish();
    }

    private void addHistory(String url) {
        if (!mHistories.contains(url)) {
            mHistories.add(0, url);
        }
    }

    private void fetchBook() {
        val url = mURL.getText().toString();
        if (!isValidURL(url)) {
            Toast.makeText(this, R.string.new_task_invalid_url, Toast.LENGTH_SHORT).show();
            mURL.requestFocus();
            return;
        }

        hideIME(this, getWindow().peekDecorView());

        Observable.<CrawlerBook>create(sub -> {
            try {
                sub.onNext(CrawlerManager.fetchBook(url, mConfig));
                sub.onCompleted();
            } catch (IOException | ParserException e) {
                sub.onError(e);
            }
        }).doOnNext(book -> cleanupBook())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> Views.animateVisibility(mProgress, true))
                .subscribe(book -> {
                    Views.animateVisibility(mProgress, false);
                    addHistory(url);
                    viewBook(book);
                }, err -> {
                    Views.animateVisibility(mProgress, false);
                    String msg;
                    if (err instanceof MalformedURLException) {
                        msg = getString(R.string.new_task_invalid_url);
                    } else {
                        msg = err.getLocalizedMessage();
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.new_task_fetch_error)
                            .setMessage(msg)
                            .setPositiveButton(R.string.ok, null)
                            .create()
                            .show();
                });
    }

    private void viewBook(CrawlerBook book) {
        mBook = book;

        mName.setText(Attributes.getTitle(book));
        val cover = Attributes.getCover(book);
        mThumbnail = null;
        if (cover != null) {
            Worker.execute(() -> Jem.cache(cover, getExternalCacheDir()), cache -> {
                mThumbnail = cache;
                val width = getResources().getDimensionPixelSize(R.dimen.task_cover_width);
                val height = getResources().getDimensionPixelSize(R.dimen.task_cover_height);
                Glide.with(this)
                        .load(cache)
                        .crossFade()
                        .override(width, height)
                        .listener(new GlideListenerAdapter(d -> {
                            Views.animateVisibility(mPlaceholder, false);
                            Views.animateVisibility(mOverview, true);
                            return false;
                        }))
                        .into(mCover);
            }, err -> {
                Log.d(TAG, "cannot load cover:" + cover, err);
            });
        }

        val items = new ArrayList<String>();
        for (val item : book.getAttributes().entries()) {
            val key = item.getKey();
            if (sIgnoredNames.contains(key)) {
                continue;
            }
            items.add(String.format("%s: %s", Attributes.titleOf(key), Variants.printable(item.getValue())));
        }
        Collections.sort(items, (s1, s2) -> compare(s1.length(), s2.length()));
        mInfo.setText(StringUtils.join("\n", items));
        mIntro.setText(Attributes.getIntro(book).getText());
    }

    private int compare(int x, int y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    private static final Collection<String> sIgnoredNames = CollectionUtils.setOf(
            Attributes.COVER, Attributes.INTRO, Attributes.TITLE
    );
}
