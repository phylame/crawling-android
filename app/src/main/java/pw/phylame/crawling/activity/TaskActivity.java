package pw.phylame.crawling.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.Pair;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jem.Attributes;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import jem.crawler.CrawlerContext;
import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;
import jem.epm.util.ParserException;
import jem.util.Variants;
import lombok.val;
import pw.phylame.commons.io.IOUtils;
import pw.phylame.commons.util.CollectionUtils;
import pw.phylame.commons.util.StringUtils;
import pw.phylame.crawling.R;
import pw.phylame.crawling.Workers;
import pw.phylame.crawling.model.DataHub;
import pw.phylame.crawling.util.JemUtils;
import pw.phylame.support.Views;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static pw.phylame.support.Views.hideIME;
import static pw.phylame.support.Views.viewById;

public class TaskActivity extends BaseActivity {
    public static final String URL_KEY = "task.url";
    public static final String FORMAT_KEY = "task.format";
    public static final String OUTPUT_KEY = "task.output";
    public static final String BACKUP_KEY = "task.backup";
    public static final String DATA_KEY = "task.data";

    private static final String HISTORY_NAME = "url-history";
    private static final String ENCODING = "UTF-16LE";

    private AutoCompleteTextView mURL;
    private Spinner mFormat;
    private TextView mPath;

    private View mProgress;

    private View mTip;
    private View mOverview;
    private TextView mName;
    private TextView mAuthor;
    private TextView mInfo;
    private TextView mIntro;
    private ImageView mCover;
    private CheckBox mBackup;

    private List<String> mHistories;
    private ArrayAdapter<String> mAdapter;

    private boolean mHasCover;
    private CrawlerBook mBook;
    private CrawlerConfig mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);
        setupCenteredToolbar(R.id.toolbar);
        setTitle(R.string.activity_new_task_title);

        mURL = viewById(this, R.id.url);
        findViewById(R.id.view).setOnClickListener(v -> fetchBook());
        mFormat = viewById(this, R.id.formats);
        mPath = viewById(this, R.id.path);
        findViewById(R.id.path_bar).setOnClickListener(v -> {
            new Intent(Intent.ACTION_VIEW);
        });
        mOverview = viewById(this, R.id.overview);
        mTip = viewById(this, R.id.tip);
        mInfo = viewById(this, R.id.info);
        mIntro = viewById(this, R.id.intro);
        mCover = viewById(this, R.id.cover);
        mName = viewById(this, R.id.name);
        mAuthor = viewById(this, R.id.author);
        mBackup = viewById(this, R.id.backup);
        mProgress = viewById(this, R.id.progress);

        mConfig = new CrawlerConfig();

        val prefs = getSharedPreferences("general", MODE_PRIVATE);
        mURL.setThreshold(prefs.getInt("url.threshold", 4));
        mPath.setText(prefs.getString("out.path", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()));
        mBackup.setChecked(prefs.getBoolean("out.backup", false));

        initHistory(prefs);

        initFormat(prefs);
    }

    private void initHistory(SharedPreferences prefs) {
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
        Workers.execute(() -> {
            val file = new File(getFilesDir(), HISTORY_NAME);
            if (file.exists()) {
                return IOUtils.toLines(file, ENCODING, true);
            } else {
                return null;
            }
        }, urls -> mAdapter.addAll(urls));
    }

    private void initFormat(SharedPreferences prefs) {
        Workers.execute(() -> {
            JemUtils.init();
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
    protected void onDestroy() {
        super.onDestroy();
        Workers.execute(() -> {
            if (mBook != null) {
                cleanupBook();
            }
            if (!mHistories.isEmpty()) {
                val file = new File(getFilesDir(), HISTORY_NAME);
                try {
                    IOUtils.writeLines(file, mHistories, "\n", ENCODING);
                } catch (IOException e) {
                    Log.e(TAG, "cannot save histories to file: " + file, e);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("format", mFormat.getSelectedItemPosition());
        outState.putInt("tip", mTip.getVisibility());
        if (mHasCover) {
            outState.putParcelable("cover", ((BitmapDrawable) mCover.getDrawable()).getBitmap());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mFormat.post(() -> mFormat.setSelection(savedInstanceState.getInt("format")));
        val visibility = savedInstanceState.getInt("tip", View.VISIBLE);
        if (visibility == View.GONE) {
            mTip.setVisibility(View.GONE);
            mOverview.setVisibility(View.VISIBLE);
        } else {
            mTip.setVisibility(View.VISIBLE);
            mOverview.setVisibility(View.GONE);
        }
        val bmp = savedInstanceState.getParcelable("cover");
        if (bmp != null) {
            mHasCover = true;
            mCover.setImageBitmap((Bitmap) bmp);
        }
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

    private void onDone() {
        val url = mURL.getText().toString();
        if (url.isEmpty()) {
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
        val lastFormat = prefs.getBoolean("task.lastFormat", false);
        val lastOutput = prefs.getBoolean("task.lastOutput", false);
        val editor = prefs.edit();
        if (lastFormat) { // use last format
            editor.putString("out.format", format);
        }
        if (lastOutput) { // use last output
            editor.putString("out.output", output);
        }
        editor.apply();

        val data = new Intent();
        data.putExtra(URL_KEY, url);
        data.putExtra(FORMAT_KEY, format);
        data.putExtra(OUTPUT_KEY, output);
        data.putExtra(BACKUP_KEY, backup);
        if (mBook != null) {
            data.putExtra(DATA_KEY, 100);
            DataHub.put(100, Pair.create(mBook, mConfig));
        }
        setResult(RESULT_OK, data);
        finish();
    }

    private void cleanupBook() {
        mBook.cleanup();
    }

    private void fetchBook() {
        val url = mURL.getText().toString();
        if (url.isEmpty()) {
            return;
        }

        hideIME(this, getWindow().peekDecorView());

        Observable.<CrawlerBook>create(sub -> {
            try {
                val crawler = CrawlerManager.crawlerFor(url);
                crawler.init(new CrawlerContext(url, mConfig));
                crawler.fetchAttributes();
                sub.onNext(crawler.getContext().getBook());
            } catch (IOException | ParserException e) {
                sub.onError(e);
            }
        }).doOnNext(book -> {
            if (mBook != null) {
                cleanupBook();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> Views.showAnimated(mProgress, true))
                .subscribe(book -> {
                    Views.showAnimated(mProgress, false);
                    if (!mHistories.contains(url)) {
                        mHistories.add(0, url);
                    }
                    viewBook(book);
                }, error -> {
                    Views.showAnimated(mProgress, false);
                    error.printStackTrace();
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.new_task_fetch_error)
                            .setMessage(error.toString())
                            .setPositiveButton(R.string.ok, null)
                            .create()
                            .show();
                });
    }

    private void viewBook(CrawlerBook book) {
        mBook = book;
        mTip.setVisibility(View.GONE);
        mOverview.setVisibility(View.VISIBLE);

        mName.setText(Attributes.getTitle(book));
        mAuthor.setText(Attributes.getAuthor(book));

        mCover.setImageResource(R.mipmap.ic_book);
        val cover = Attributes.getCover(book);
        if (cover != null) {
            Workers.execute(() -> {
                val bmp = BitmapFactory.decodeStream(cover.openStream());
                val height = getResources().getDimensionPixelSize(R.dimen.new_task_cover_height);
                return ThumbnailUtils.extractThumbnail(bmp, (int) (height * 0.75), height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            }, bmp -> {
                mHasCover = true;
                mCover.setImageBitmap(bmp);
            }, err -> {
                mHasCover = false;
                Log.d(TAG, "cannot load cover:" + cover);
            });
        } else {
            mHasCover = false;
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
            Attributes.COVER, Attributes.INTRO, Attributes.TITLE, Attributes.AUTHOR
    );
}
