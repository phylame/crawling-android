package pw.phylame.crawling.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jem.Attributes;
import jem.Chapter;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;
import jem.epm.util.ParserException;
import lombok.val;
import pw.phylame.commons.util.Validate;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.crawling.Workers;
import pw.phylame.crawling.model.DataHub;
import pw.phylame.crawling.model.ITask;
import pw.phylame.crawling.model.ITaskManager;
import pw.phylame.crawling.model.TaskEvent;
import pw.phylame.crawling.service.TaskService;
import pw.phylame.crawling.util.JemUtils;
import pw.phylame.support.Activities;
import pw.phylame.support.RxBus;
import pw.phylame.support.TimedAction;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static pw.phylame.support.Views.viewById;

public class CrawlerActivity extends BaseActivity {
    public static final int REQUEST_TASK = 100;

    private View mPlaceholder;
    private TaskAdapter mAdapter;
    private ActionMode mActionMode;
    private TimedAction mExitAction;
    private ITaskManager mTaskManager;
    private Subscription mSubscription;
    private RecyclerView mRecycler;
    private Set<ITask> mSelections = new LinkedHashSet<>();

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (name.getShortClassName().equals(".service.TaskService")) {
                mTaskManager = (ITaskManager) service;
                mAdapter.mTasks.clear();
                mAdapter.onTasksAdded(mTaskManager.tasks());
                mPlaceholder.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);

                if (mTaskData != null) {
                    prepareTask(mTaskData);
                    mTaskData = null;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "lost connection to " + name);
            if (name.getShortClassName().equals(".service.TaskService")) {
                mTaskManager = null;
                unregisterEvents();
            }
        }
    };

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_task, menu);
            mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount(), TaskEvent.EVENT_LIFECYCLE);
            mSelections.clear();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_start: {
                    ensureTaskServiceBound();
                    mTaskManager.startTasks(mSelections, true);
                }
                break;
                case R.id.action_pause: {
                    ensureTaskServiceBound();
                    mTaskManager.startTasks(mSelections, false);
                }
                break;
                case R.id.action_delete: {
                    deleteTasks(mSelections);
                }
                break;
                case R.id.action_select_all: {
                    toggleSelections();
                }
                break;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            mSelections.clear();
            for (val task : mAdapter.mTasks) {
                task.selected = false;
            }
            mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount(), TaskEvent.EVENT_LIFECYCLE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crawler);
        setSupportActionBar(viewById(this, R.id.toolbar));
        mPlaceholder = findViewById(R.id.placeholder);

        initRecycler();
        findViewById(R.id.fab).setOnClickListener(v -> newTask());

        mExitAction = new TimedAction(getResources().getInteger(R.integer.exit_delay_millis));
    }

    private void initRecycler() {
        mAdapter = new TaskAdapter();
        mRecycler = viewById(this, R.id.recycler);
        mRecycler.setAdapter(mAdapter);
        mRecycler.setHasFixedSize(true);
        mRecycler.setLayoutManager(new LinearLayoutManager(this));
        mRecycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    @Override
    protected void onStart() {
        super.onStart();
        val intent = new Intent(this, TaskService.class);
        startService(intent);
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerEvents();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterEvents();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTaskManager = null;
        unbindService(mServiceConnection);
    }

    private void unregisterEvents() {
        if (mSubscription != null && !mSubscription.isUnsubscribed()) {
            mSubscription.unsubscribe();
        }
        mSubscription = null;
    }

    private void registerEvents() {
        Validate.require(mSubscription == null, "Unsubscribe firstly");
        mSubscription = RxBus
                .getDefault()
                .subscribe(TaskEvent.class, e -> runOnUiThread(() -> handleEvent(e)));
    }

    private void handleEvent(TaskEvent e) {
        switch (e.type) {
            case TaskEvent.EVENT_PROGRESS: {
                mAdapter.notifyItemChanged(e.arg1, e.obj);
            }
            break;
            case TaskEvent.EVENT_LIFECYCLE: {
                mAdapter.notifyItemChanged(e.arg1, TaskEvent.EVENT_LIFECYCLE);
            }
            break;
            case TaskEvent.EVENT_SUBMIT: {
                mAdapter.onTaskAdded((ITask) e.obj);
                mPlaceholder.setVisibility(View.GONE);
                mRecycler.smoothScrollToPosition(mAdapter.getItemCount());
            }
            break;
            case TaskEvent.EVENT_FETCHED: {
                mAdapter.notifyItemChanged(e.arg1);
            }
            break;
            case TaskEvent.EVENT_DELETE: {
                mSelections.remove(e.obj);
                mAdapter.onTaskRemoved(e.arg1);
                if (isInSelection()) {
                    if (mAdapter.getItemCount() == 0) {
                        mActionMode.finish();
                    } else {
                        updateActionMenus();
                    }
                }
                if (mAdapter.getItemCount() == 0) {
                    mPlaceholder.setVisibility(View.VISIBLE);
                }
            }
            break;
        }
        e.recycle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_crawler, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                Activities.startActivityForResult(this, TaskActivity.class, REQUEST_TASK);
                break;
            case R.id.action_edit:
                beginSelections();
                break;
            case R.id.action_settings:
                Activities.startActivity(this, SettingsActivity.class);
                break;
            case R.id.action_exit:
                exitApp();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private Intent mTaskData;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_TASK: {
                if (resultCode == RESULT_OK) {
                    mTaskData = data;
                }
            }
            break;
            default: {
                super.onActivityResult(requestCode, resultCode, data);
            }
            break;
        }
    }

    @Override
    public void onBackPressed() {
        if (isCompleted()) {
            if (mExitAction.isEnable()) {
                exitApp0();
            } else {
                Toast.makeText(this, R.string.exit_delay_tip, Toast.LENGTH_SHORT).show();
            }
        } else {
            exitApp();
        }
    }

    private void exitApp() {
        if (isCompleted()) {
            exitApp0();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.exit_title)
                    .setPositiveButton(R.string.ok, ((dialog, which) -> exitApp0()))
                    .setPositiveButton(R.string.discard, null)
                    .create()
                    .show();
        }
    }

    private void exitApp0() {
        CrawlerApp.sharedApp().finish();
        System.exit(0);
    }

    private boolean isCompleted() {
        return true;
    }

    private boolean isInSelection() {
        return mActionMode != null;
    }

    private boolean beginSelections() {
        if (mActionMode == null) {
            mActionMode = startSupportActionMode(mActionModeCallback);
            updateActionMenus();
            return true;
        }
        return false;
    }

    private void toggleSelection(ITask task, int position) {
        task.selected = !task.selected;
        if (task.selected) {
            mSelections.add(task);
        } else {
            mSelections.remove(task);
        }
        mAdapter.notifyItemChanged(position, TaskEvent.EVENT_LIFECYCLE);
        updateActionMenus();
    }

    private void toggleSelections() {
        val tasks = mAdapter.mTasks;
        val selected = mSelections.size() != tasks.size();
        for (int i = 0, end = tasks.size(); i < end; ++i) {
            tasks.get(i).selected = selected;
        }
        if (selected) {
            mSelections.addAll(tasks);
        } else {
            mSelections.clear();
        }
        updateActionMenus();
        mAdapter.notifyItemRangeChanged(0, tasks.size(), TaskEvent.EVENT_LIFECYCLE);
    }

    private void updateActionMenus() {
        val size = mSelections.size();
        val menu = mActionMode.getMenu();
        menu.findItem(R.id.action_start).setEnabled(size > 0);
        menu.findItem(R.id.action_pause).setEnabled(size > 0);
        menu.findItem(R.id.action_delete).setEnabled(size > 0);
        mActionMode.setTitle(getResources().getQuantityString(R.plurals.selection_title, size, size));
    }

    private void ensureTaskServiceBound() {
        Validate.requireNotNull(mTaskManager, "Bind to task service first");
    }

    private void newTask() {
        ensureTaskServiceBound();
        String[] permissions = {Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissions, granted -> {
            if (granted) {
                // TODO: 2017/2/28 get task in new activity
                demoFetch();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void prepareTask(Intent data) {
        if (mTaskManager == null) {
            Log.d(TAG, "not bind");
            return;
        }
        val task = mTaskManager.newTask();
        task.setFormat(data.getStringExtra(TaskActivity.FORMAT_KEY));
        task.setBackup(data.getBooleanExtra(TaskActivity.BACKUP_KEY, false));
        task.setOutput(new File(data.getStringExtra(TaskActivity.OUTPUT_KEY)));
        val key = data.getIntExtra(TaskActivity.DATA_KEY, -1);
        if (key != -1) {
            Pair<CrawlerBook, CrawlerConfig> pair = DataHub.take(key);
            task.setBook(pair.first);
        } else { // no fetched book
            task.setURL(data.getStringExtra(TaskActivity.URL_KEY));
        }
        mTaskManager.submitTask(task);
    }

    private void viewTask(ITask task) {

    }

    private void deleteTasks(Collection<ITask> tasks) {
        val size = mAdapter.getItemCount();
        if (size == 0) {
            return;
        }
        boolean done = true;
        for (val task : tasks) {
            if (task.getState() != ITask.State.Finished) {
                done = false;
            }
        }
        if (done) { // all tasks done
            mTaskManager.deleteTasks(mSelections);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getResources().getQuantityString(R.plurals.delete_task_tip, size, size))
                .setPositiveButton(R.string.ok, (dialog, which) -> mTaskManager.deleteTasks(mSelections))
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    int i = 0;

    private void demoFetch() {
        String[] urls = {
                "http://book.qidian.com/info/3358998",
                "http://book.qidian.com/info/1003624460",
                "http://www.mangg.com/id54541/",
                "http://www.mangg.com/id52167/",
                "http://book.qidian.com/info/2866988",
                "http://book.qidian.com/info/1003353494",
                "http://book.qidian.com/info/1003818949",
                "http://book.qidian.com/info/3368425",
                "http://www.mangg.com/id53148/",
                "http://www.mangg.com/id54504/"
        };
        val config = new CrawlerConfig();
        Observable.<CrawlerBook>create(sub -> {
            try {
                sub.onNext(CrawlerManager.fetchBook(urls[i++], config));
            } catch (IOException | ParserException e) {
                sub.onError(e);
            }
        }).doOnSubscribe(JemUtils::init)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(book -> {
                    val task = mTaskManager.newTask();
                    task.setBook(book);
                    task.setOutput(new File(Environment.getExternalStorageDirectory(), Attributes.getTitle(book) + ".pmab"));
                    task.setFormat(EpmManager.PMAB);
                    mTaskManager.submitTask(task);
                }, e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
        private final Lazy<Integer> mCoverHeight = new Lazy<>(() -> {
            int[] attrs = {R.attr.listPreferredItemHeight};
            val typedValue = new TypedValue();
            return obtainStyledAttributes(typedValue.resourceId, attrs).getDimensionPixelSize(0, -1) - getResources().getDimensionPixelSize(R.dimen.book_cover_margin);
        });

        private final Lazy<Integer> mCoverWidth = new Lazy<>(() -> (int) (mCoverHeight.get() * 0.75));

        private final LayoutInflater mInflater;
        private final List<ITask> mTasks = new ArrayList<>();

        TaskAdapter() {
            mInflater = LayoutInflater.from(CrawlerActivity.this);
        }

        private void onTaskAdded(ITask task) {
            mTasks.add(task);
            notifyItemInserted(mTasks.size() - 1);
        }

        private void onTasksAdded(Collection<? extends ITask> tasks) {
            mTasks.addAll(tasks);
            notifyDataSetChanged();
        }

        private void onTaskRemoved(int position) {
            mTasks.remove(position);
            notifyItemRemoved(position);
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }

        @Override
        public TaskHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            val holder = new TaskHolder(mInflater.inflate(R.layout.task_list_item, parent, false));
            setupListeners(holder);
            return holder;
        }

        private void setupListeners(TaskHolder holder) {
            holder.itemView.setOnClickListener(v -> {
                val task = (ITask) v.getTag(R.id.tag_task);
                if (isInSelection()) {
                    toggleSelection(task, (int) v.getTag(R.id.tag_position));
                } else {
                    viewTask(task);
                }
            });
            holder.itemView.setOnLongClickListener(v -> !isInSelection() && beginSelections());
            holder.option.setOnClickListener(v -> {
                val task = (ITask) v.getTag(R.id.tag_task);
                if (isInSelection()) {
                    toggleSelection(task, (int) v.getTag(R.id.tag_position));
                } else {
                    switch (task.getState()) {
                        case Started: {
                            mTaskManager.startTask(task, false);
                        }
                        break;
                        case Paused: {
                            mTaskManager.startTask(task, true);
                        }
                        break;
                        case Finished: {
                            viewTask(task);
                        }
                        break;
                    }
                }
            });
        }

        @Override
        public void onBindViewHolder(TaskHolder holder, int position) {
            // ignored by onBindViewHolder(holder, position, payloads)
        }

        @Override
        public void onBindViewHolder(TaskHolder holder, int position, List<Object> payloads) {
            val task = mTasks.get(position);
            holder.itemView.setTag(R.id.tag_task, task);
            holder.itemView.setTag(R.id.tag_position, position);
            holder.option.setTag(R.id.tag_task, task);
            holder.option.setTag(R.id.tag_position, position);
            if (payloads.isEmpty()) {
                bindData(holder, task); // update all
            } else {
                val o = payloads.get(0);
                if (o instanceof Chapter) { // update progress
                    bindData(holder, task, TaskEvent.EVENT_PROGRESS);
                    holder.intro.setText(Attributes.getTitle((Chapter) o));
                } else if (o instanceof Integer) { // update event
                    bindData(holder, task, (int) o);
                }
            }
        }

        private void bindData(TaskHolder holder, ITask task) {
            val book = task.getBook();
            if (task.cover != null) {
                holder.icon.setImageDrawable(task.cover);
            } else {
                holder.icon.setImageResource(R.mipmap.ic_book);
                if (book != null) {
                    val cover = Attributes.getCover(book);
                    if (cover != null) {
                        Workers.execute(() -> {
                            val bmp = BitmapFactory.decodeStream(cover.openStream());
                            val m = ThumbnailUtils.extractThumbnail(bmp, mCoverWidth.get(), mCoverHeight.get());
                            bmp.recycle();
                            return m;
                        }, bmp -> {
                            holder.icon.setImageBitmap(bmp);
                            task.cover = holder.icon.getDrawable();
                        }, err -> Log.d(TAG, "cannot load cover:" + cover));
                    }
                }
            }
            holder.name.setText(book != null ? Attributes.getTitle(book) : "------");
            holder.intro.setText(book != null ? Attributes.getAuthor(book) : "--------");
            holder.progress.setMax(task.getTotal());
            holder.progress.setProgress(task.getProgress());
            holder.info.setText(task.getProgress() + "/" + task.getTotal());
            setOptionIcon(holder.option, task, isInSelection());
        }

        private void bindData(TaskHolder holder, ITask task, int event) {
            switch (event) {
                case TaskEvent.EVENT_LIFECYCLE: {
                    setOptionIcon(holder.option, task, isInSelection());
                }
                break;
                case TaskEvent.EVENT_PROGRESS: {
                    holder.progress.setProgress(task.getProgress());
                    holder.info.setText(task.getProgress() + "/" + task.getTotal());
                }
                break;
            }
        }

        private void setOptionIcon(ImageView view, ITask task, boolean isSelection) {
            view.setImageResource(optionIconOf(task, isSelection));
            view.setColorFilter(ContextCompat.getColor(CrawlerActivity.this, R.color.colorAccent), PorterDuff.Mode.MULTIPLY);
        }

        private int optionIconOf(ITask task, boolean isSelection) {
            if (isSelection) {
                return task.selected ? R.mipmap.ic_checked_checkbox : R.mipmap.ic_unchecked_checkbox;
            }
            switch (task.getState()) {
                case Started:
                    return R.mipmap.ic_pause;
                case Paused:
                case Submitted:
                    return R.mipmap.ic_play;
                case Finished:
                    return R.mipmap.ic_view_details;
                case Failed:
                    return R.mipmap.ic_error;
                default:
                    return R.mipmap.ic_view_details;
            }
        }
    }

    private static class TaskHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final ImageView icon;
        final ImageButton option;
        final ProgressBar progress;
        final TextView intro;
        final TextView info;

        TaskHolder(View view) {
            super(view);
            icon = viewById(view, R.id.cover);
            name = viewById(view, R.id.name);
            option = viewById(view, R.id.option);
            progress = viewById(view, R.id.progress);
            intro = viewById(view, R.id.intro);
            info = viewById(view, R.id.info);
        }
    }
}
