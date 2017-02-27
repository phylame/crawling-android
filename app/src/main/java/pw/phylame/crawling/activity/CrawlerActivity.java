package pw.phylame.crawling.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.os.EnvironmentCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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
import java.util.List;

import jem.Attributes;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;
import jem.epm.util.ParserException;
import lombok.val;
import pw.phylame.commons.util.RandomUtils;
import pw.phylame.commons.util.Validate;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.crawling.model.ITask;
import pw.phylame.crawling.model.ITaskManager;
import pw.phylame.crawling.model.TaskEvent;
import pw.phylame.crawling.service.TaskService;
import pw.phylame.crawling.task.Task;
import pw.phylame.support.Activities;
import pw.phylame.support.RxBus;
import pw.phylame.support.TimedAction;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static pw.phylame.support.Views.viewById;

public class CrawlerActivity extends BaseActivity {
    private View mPlaceholder;
    private TaskAdapter mAdapter;
    private ActionMode mActionMode;
    private TimedAction mExitAction;
    private ITaskManager mTaskManager;
    private Subscription mSubscription;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ITaskManager) {
                Log.d(TAG, "got connection to " + name + ", " + service);
                mTaskManager = (ITaskManager) service;
                mAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "lost connection to service " + name);
            mTaskManager = null;
            unregisterEvents();
        }
    };

    private ActionMode.Callback mCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }
    };

    private View.OnClickListener mListener = v -> {
        switch (v.getId()) {
            case R.id.fab: {
                newTask();
            }
            break;
            case R.id.bottom_bar: {
                Activities.startActivity(this, HistoryActivity.class);
            }
            break;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crawler);
        setSupportActionBar(viewById(this, R.id.toolbar));
        mPlaceholder = findViewById(R.id.placeholder);
        initRecycler();

        findViewById(R.id.fab).setOnClickListener(mListener);
        findViewById(R.id.bottom_bar).setOnClickListener(mListener);

        mExitAction = new TimedAction(getResources().getInteger(R.integer.exit_check_millis));
    }

    private void initRecycler() {
        mAdapter = new TaskAdapter();
        RecyclerView recycler = viewById(this, R.id.recycler);
        recycler.setAdapter(mAdapter);
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    @Override
    protected void onStart() {
        super.onStart();
        val intent = new Intent(this, TaskService.class);
        startService(intent);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
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
        unbindService(mConnection);
    }

    private void unregisterEvents() {
        if (mSubscription != null && !mSubscription.isUnsubscribed()) {
            Log.d(TAG, "unregister from RxBus");
            mSubscription.unsubscribe();
        }
        mSubscription = null;
    }

    private void registerEvents() {
        System.out.println("CrawlerActivity.registerEvents");
        Validate.require(mSubscription == null, "Must unsubscribe firstly");
        mSubscription = RxBus.getDefault().subscribe(TaskEvent.class, e -> {
            runOnUiThread(() -> {
                handleEvent(e);
            });
        });
        Log.d(TAG, "register to RxBus");
    }

    private void handleEvent(TaskEvent e) {
        switch (e.type) {
            case TaskEvent.EVENT_PROGRESS: {
                mAdapter.notifyItemChanged(e.arg1, new int[]{e.arg1, e.arg2});
            }
            break;
            case TaskEvent.EVENT_LIFECYCLE: {
                mAdapter.notifyItemChanged(e.arg1, TaskEvent.EVENT_LIFECYCLE);
            }
            break;
            case TaskEvent.EVENT_SUBMIT: {
                mAdapter.addTask((ITask) e.obj);
                mPlaceholder.setVisibility(View.GONE);
            }
            break;
            case TaskEvent.EVENT_DELETE: {
                mAdapter.removeTask(e.arg1);
                if (mAdapter.getItemCount() == 0) {
                    mPlaceholder.setVisibility(View.VISIBLE);
                }
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
        System.out.println("item = " + item);
        switch (item.getItemId()) {
            case R.id.action_add:
                Activities.startActivity(this, NewTaskActivity.class);
                break;
            case R.id.action_edit:
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
        if (mExitAction.isEnable()) {
            exitApp();
        } else {
            Toast.makeText(this, R.string.exit_check_tip, Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isInSelection() {
        return false;
    }

    private boolean mCrawlerLoaded = false;

    private void ensureTaskServiceBound() {
        Validate.requireNotNull(mTaskManager, "bind to task service first");
    }

    private void newTask() {
        ensureTaskServiceBound();
        String[] permissions = {Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissions, granted -> {
            if (granted) {
                demoFetch();
            } else {
                Toast.makeText(this, "cannot grant internet permission", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void demoFetch() {
        String[] urls = {"http://book.qidian.com/info/3358998", "http://book.qidian.com/info/1003624460"};
        val config = new CrawlerConfig();
        Observable.<CrawlerBook>create(sub -> {
            try {
                sub.onNext(CrawlerManager.fetchBook(RandomUtils.anyOf(urls), config));
            } catch (IOException | ParserException e) {
                sub.onError(e);
            }
        }).doOnSubscribe(() -> {
            if (!mCrawlerLoaded) {
                CrawlerManager.loadCrawlers();
                EpmManager.loadImplementors();
                mCrawlerLoaded = true;
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(book -> {
                    val task = mTaskManager.newTask();
                    task.setBook(book);
                    task.setOutput(new File(Environment.getExternalStorageDirectory(), Attributes.getTitle(book) + ".pmab"));
                    task.setFormat("pmab");
                    mTaskManager.submitTask(task);
                }, Throwable::printStackTrace);
    }

    private void exitApp() {
        CrawlerApp.sharedApp().finish();
        System.exit(0);
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
        private final Lazy<Drawable> mDefaultCover = new Lazy<>(() -> {
            return ContextCompat.getDrawable(CrawlerActivity.this, R.mipmap.ic_launcher);
        });

        private final Lazy<Integer> mCoverHeight = new Lazy<Integer>(() -> {
            int[] attribute = new int[]{android.R.attr.listPreferredItemHeight};
            val typedValue = new TypedValue();
            TypedArray array = CrawlerActivity.this.obtainStyledAttributes(typedValue.resourceId, attribute);
            return array.getDimensionPixelSize(0, -1);
        });

        private final Lazy<Integer> mCoverWidth = new Lazy<Integer>(() -> {
            return (int) (mCoverHeight.get() * 0.75);
        });

        private final LayoutInflater mInflater;
        private final List<ITask> mTasks = new ArrayList<>();

        private TaskAdapter() {
            mInflater = LayoutInflater.from(CrawlerActivity.this);
        }

        private void addTask(ITask task) {
            mTasks.add(task);
            notifyItemInserted(mTasks.size() - 1);
        }

        private void removeTask(int position) {
            mTasks.remove(position);
            notifyItemRemoved(position);
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }

        @Override
        public TaskHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new TaskHolder(mInflater.inflate(R.layout.task_item, parent, false));
        }

        @Override
        public void onBindViewHolder(TaskHolder holder, int position) {
            // ignored by onBindViewHolder(holder, position, payloads)
        }

        @Override
        public void onBindViewHolder(TaskHolder holder, int position, List<Object> payloads) {
            val task = mTasks.get(position);
            if (payloads.isEmpty()) {
                bindData(holder, task);
            } else {
                val o = payloads.get(0);
                if (o instanceof Integer) {
                    bindData(holder, task, (int) o);
                } else if (o instanceof int[]) {
                    val progress = ((int[]) o)[1];
                    holder.progress.setProgress(progress);
                    holder.info.setText(progress + "/" + task.getTotal());
                }
            }
//            setupListener(holder, task);
        }

        private void bindData(TaskHolder holder, ITask task) {
            if (task.cover != null) {

            }
            holder.icon.setImageResource(R.mipmap.ic_launcher);
            holder.name.setText(task.getName());
            val total = task.getTotal();
            holder.progress.setMax(total);
            if (task.getState() == ITask.State.Finished) {
                holder.progress.setProgress(total);
                holder.info.setText(total + "/" + total);
            } else {
                holder.progress.setProgress(0);
                holder.info.setText("0/" + total);
            }
            setOptionIcon(holder.option, task, isInSelection());
        }

        private void bindData(TaskHolder holder, ITask task, int event) {
            switch (event) {
                case TaskEvent.EVENT_LIFECYCLE: {
                    setOptionIcon(holder.option, task, isInSelection());
                }
                break;
            }
        }

        private void setOptionIcon(ImageView view, ITask task, boolean isSelection) {
            view.setImageResource(optionIconOf(task, isInSelection()));
            view.setColorFilter(ContextCompat.getColor(CrawlerActivity.this, R.color.colorForeground), PorterDuff.Mode.MULTIPLY);
        }

        private int optionIconOf(ITask task, boolean isSelection) {
            if (isSelection) {
                return task.selected
                        ? R.mipmap.ic_checked_checkbox
                        : R.mipmap.ic_unchecked_checkbox;
            }
            switch (task.getState()) {
                case Started:
                    return R.mipmap.ic_pause;
                case Paused:
                    return R.mipmap.ic_play;
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
        final TextView info;

        TaskHolder(View view) {
            super(view);
            icon = viewById(view, R.id.icon);
            name = viewById(view, R.id.name);
            option = viewById(view, R.id.option);
            progress = viewById(view, R.id.progressBar);
            info = viewById(view, R.id.info);
        }
    }
}
