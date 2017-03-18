package pw.phylame.crawling.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jem.Attributes;
import lombok.val;
import pw.phylame.commons.util.Validate;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.crawling.model.ITask;
import pw.phylame.crawling.model.ITaskManager;
import pw.phylame.crawling.service.TaskService;
import pw.phylame.support.Activities;
import pw.phylame.support.DataHub;
import pw.phylame.support.RxBus;
import pw.phylame.support.TimedAction;
import pw.phylame.support.Worker;
import rx.Subscription;

import static pw.phylame.support.Views.viewById;

public class CrawlerActivity extends BaseActivity {
    public static final int REQUEST_TASK = 100;

    private View mPlaceholder;
    private TaskAdapter mAdapter;
    private RecyclerView mRecycler;
    private ActionMode mActionMode;
    private TimedAction mExitAction;
    private ITaskManager mTaskManager;
    private Subscription mSubscription;
    private Set<ITask> mSelections = new LinkedHashSet<>();

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (name.getClassName().equals(TaskService.class.getName())) {
                mTaskManager = (ITaskManager) service;
                mAdapter.mTasks.clear();
                mAdapter.onTasksAdded(mTaskManager.getTasks());
                mPlaceholder.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                ITask task = DataHub.take(TaskActivity.TASK_KEY);
                if (task != null && task.isInitialized()) {
                    mTaskManager.submitTask(task);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "lost connection to " + name);
            if (name.getClassName().equals(TaskService.class.getName())) {
                mTaskManager = null;
                unregisterEvents();
            }
        }
    };

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_task, menu);
            mSelections.clear();
            mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount(), ITask.EVENT_STATE);
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
                task.setSelected(false);
            }
            mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount(), ITask.EVENT_STATE);
        }
    };

    private final EventHandle mHandle = new EventHandle(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crawler);
        setupToolbar(R.id.toolbar);
        setupColoredStatus();

        mPlaceholder = findViewById(R.id.placeholder);

        initRecycler();

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
        Validate.check(mSubscription == null, "Unsubscribe firstly");
        mSubscription = RxBus.getDefault()
                .subscribe(Message.class, mHandle::sendMessage);
    }

    private void handleEvent(Message e) {
        switch (e.what) {
            case ITask.EVENT_PROGRESS: {
                mAdapter.notifyItemChanged(e.arg1, ITask.EVENT_PROGRESS);
            }
            break;
            case ITask.EVENT_STATE: {
                mAdapter.notifyItemChanged(e.arg1, ITask.EVENT_STATE);
            }
            break;
            case ITask.EVENT_SUBMIT: {
                mAdapter.onTaskAdded(DataHub.take(e.arg2));
                mPlaceholder.setVisibility(View.GONE);
                mRecycler.smoothScrollToPosition(mAdapter.getItemCount());
            }
            break;
            case ITask.EVENT_FETCHED: { // attributes and contents is fetched
                mAdapter.notifyItemChanged(e.arg1);
            }
            break;
            case ITask.EVENT_DELETE: { // task is deleted
                ITask task = DataHub.take(e.arg2);
                Worker.execute(task::cleanup);
                mSelections.remove(task);
                mAdapter.onTaskRemoved(e.arg1);
                // TODO: 2017-3-4 add to history
                if (isInSelection()) {
                    if (mAdapter.getItemCount() == 0) {
                        mActionMode.finish();
                    } else {
                        updateActionMenus();
                    }
                }
                mPlaceholder.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            }
            break;
        }
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
                newTask();
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
                    .setMessage(R.string.exit_cancel_tip)
                    .setPositiveButton(R.string.ok, ((dialog, which) -> exitApp0()))
                    .setNegativeButton(R.string.discard, null)
                    .create()
                    .show();
        }
    }

    private void exitApp0() {
        stopService(new Intent(this, TaskService.class));
        CrawlerApp.sharedApp().cleanup();
        finish();
    }

    private boolean isCompleted() {
        for (val task : mAdapter.mTasks) {
            switch (task.getState()) {
                case Started:
                case Submitted:
                case Paused:
                    return false;
            }
        }
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
        task.setSelected(!task.isSelected());
        if (task.isSelected()) {
            mSelections.add(task);
        } else {
            mSelections.remove(task);
        }
        mAdapter.notifyItemChanged(position, ITask.EVENT_STATE);
        updateActionMenus();
    }

    private void toggleSelections() {
        val tasks = mAdapter.mTasks;
        val selected = mSelections.size() != tasks.size();
        for (int i = 0, end = tasks.size(); i < end; ++i) {
            tasks.get(i).setSelected(selected);
        }
        if (selected) {
            mSelections.addAll(tasks);
        } else {
            mSelections.clear();
        }
        updateActionMenus();
        mAdapter.notifyItemRangeChanged(0, tasks.size(), ITask.EVENT_STATE);
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
                DataHub.put(TaskActivity.TASK_KEY, mTaskManager.newTask());
                Activities.startActivityForResult(this, TaskActivity.class, REQUEST_TASK);
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void viewTask(ITask task) {
        DataHub.put(DetailsActivity.TASK_KEY, task);
        Activities.startActivity(this, DetailsActivity.class);
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

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
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
            holder.itemView.setOnLongClickListener(v -> {
                if (!isInSelection() && beginSelections()) {
                    toggleSelection((ITask) v.getTag(R.id.tag_task), (int) v.getTag(R.id.tag_position));
                    return true;
                }
                return false;
            });
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
                        default: {
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
                if (o instanceof Integer) { // for event
                    bindData(holder, task, (int) o);
                }
            }
        }

        private void bindData(TaskHolder holder, ITask task) {
            val book = task.getBook();
            if (task.getCover() != null) {
                holder.icon.setImageDrawable(task.getCover());
            } else {
                holder.icon.setImageResource(R.mipmap.ic_book);
                if (book != null) {
                    val cover = Attributes.getCover(book);
                    if (cover != null) {
                        Worker.execute(() -> {
                            val width = getResources().getDimensionPixelSize(R.dimen.book_cover_width);
                            val height = getResources().getDimensionPixelSize(R.dimen.book_cover_height);
                            val bmp = BitmapFactory.decodeStream(cover.openStream());
                            return ThumbnailUtils.extractThumbnail(bmp, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
                        }, bmp -> {
                            holder.icon.setImageBitmap(bmp);
                            task.setCover(holder.icon.getDrawable());
                        }, err -> Log.d(TAG, "cannot load cover:" + cover));
                    }
                }
            }
            holder.name.setText(book != null ? Attributes.getTitle(book) : getString(R.string.loading));
            holder.author.setText(book != null ? Attributes.getAuthor(book) : "");
            holder.progress.setProgress(task.getProgress());
            if (book != null) {
                val intro = Attributes.getIntro(book);
                holder.intro.setText(intro != null ? intro.getText().replace('\n', ' ') : "");
            } else {
                holder.intro.setText("");
            }
            holder.info.setText(task.getProgress() + "%");
            setOptionIcon(holder, task);
        }

        private void bindData(TaskHolder holder, ITask task, int event) {
            switch (event) {
                case ITask.EVENT_STATE: {
                    setOptionIcon(holder, task);
                }
                break;
                case ITask.EVENT_PROGRESS: {
                    holder.progress.setProgress(task.getProgress());
                    holder.info.setText(task.getProgress() + "%");
                }
                break;
            }
        }

        private void setOptionIcon(TaskHolder holder, ITask task) {
            val inSelection = isInSelection();
            holder.option.setImageResource(optionIconOf(task, inSelection));
            if (inSelection) {
                holder.option.setBackground(holder.optionBackground);
                return;
            }
            switch (task.getState()) {
                case Started:
                case Paused:
                case Finished:
                case Failed: {
                    holder.option.setBackground(holder.optionBackground);
                }
                break;
                default: {
                    holder.option.setBackground(null);
                }
                break;
            }
        }

        private int optionIconOf(ITask task, boolean isSelection) {
            if (isSelection) {
                return task.isSelected() ? R.mipmap.ic_checked_checkbox : R.mipmap.ic_unchecked_checkbox;
            }
            switch (task.getState()) {
                case Started:
                    return R.mipmap.ic_pause;
                case Paused:
                    return R.mipmap.ic_play;
                case Submitted:
                    return R.mipmap.ic_clock;
                case Finished:
                    return R.mipmap.ic_view_details;
                default:
                    return R.mipmap.ic_error;
            }
        }
    }

    private static class TaskHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView author;
        final ProgressBar progress;
        final TextView intro;
        final TextView info;
        final ImageButton option;
        final Drawable optionBackground;

        TaskHolder(View view) {
            super(view);
            icon = viewById(view, R.id.cover);
            name = viewById(view, R.id.name);
            author = viewById(view, R.id.author);
            progress = viewById(view, R.id.progress);
            intro = viewById(view, R.id.intro);
            info = viewById(view, R.id.info);
            option = viewById(view, R.id.option);
            optionBackground = option.getBackground();
        }
    }

    private static class EventHandle extends Handler {
        private final WeakReference<CrawlerActivity> mActivity;

        EventHandle(CrawlerActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            mActivity.get().handleEvent(msg);
        }
    }
}
