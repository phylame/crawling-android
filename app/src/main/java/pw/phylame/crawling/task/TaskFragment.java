package pw.phylame.crawling.task;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import lombok.val;
import pw.phylame.commons.util.Validate;
import pw.phylame.crawling.R;
import pw.phylame.support.RxBus;
import rx.Subscription;

import static pw.phylame.support.Views.viewById;

public class TaskFragment extends Fragment implements ActionMode.Callback, ServiceConnection {
    private static final String TAG = TaskFragment.class.getSimpleName();

    private View mPlaceholder;
    private TaskAdapter mAdapter;
    private ActionMode mActionMode;
    private ITaskManager mTaskManager;
    private Subscription mSubscription;

    public TaskFragment() {
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment TaskFragment.
     */
    public static TaskFragment newInstance() {
        return new TaskFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        val context = getContext();
        val intent = new Intent(context, TaskService.class);
        context.startService(intent);
        // TODO: 2017-2-22 stop service when app exit
        context.bindService(intent, this, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bind to task service");
    }

    @Override
    public void onResume() {
        super.onResume();
        registerEvents();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterEvents();
    }

    @Override
    public void onStop() {
        super.onStop();
        mTaskManager = null;
        getContext().unbindService(this);
        Log.d(TAG, "unbind from task service");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        val view = inflater.inflate(R.layout.content_task, container, false);
        mPlaceholder = view.findViewById(R.id.placeholder);
        initRecycler(view);
        return view;
    }

    private void initRecycler(View view) {
        mAdapter = new TaskAdapter();
        RecyclerView recycler = viewById(view, R.id.recycler);
        recycler.setAdapter(mAdapter);
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_task, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                newTask();
                break;
            case R.id.action_edit:
                startSelection();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_task_manage, menu);
        mSelections.clear();
        mAdapter.notifyDataSetChanged(); // refresh selection state
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_start:
                startTasks(mSelections, true);
                break;
            case R.id.action_pause:
                startTasks(mSelections, false);
                break;
            case R.id.action_delete:
                deleteTasks(mSelections);
                break;
            case R.id.action_select_all:
                toggleSelections();
                break;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        mSelections.clear();
        mAdapter.notifyDataSetChanged(); // refresh selection state
    }

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

    private static final Random RANDOM = new Random();

    private void unregisterEvents() {
        if (mSubscription != null && !mSubscription.isUnsubscribed()) {
            Log.d(TAG, "unregister from rxbus");
            mSubscription.unsubscribe();
        }
        mSubscription = null;
    }

    private void registerEvents() {
        Validate.require(mSubscription == null, "Must unsubscribe firstly");
        mSubscription = RxBus.getDefault().subscribe(TaskEvent.class, e -> {
            getActivity().runOnUiThread(() -> {
                handleEvent(e);
            });
        });
        Log.d(TAG, "register to rxbus");
    }

    private void handleEvent(TaskEvent e) {
        val tasks = mAdapter.mTasks;
        switch (e.getType()) {
            case TaskEvent.EVENT_PROGRESS: {
                mAdapter.notifyItemChanged(e.getArg1(), TaskEvent.EVENT_PROGRESS);
            }
            break;
            case TaskEvent.EVENT_LIFECYCLE: {
                mAdapter.notifyItemChanged(e.getArg1(), TaskEvent.EVENT_LIFECYCLE);
            }
            break;
            case TaskEvent.EVENT_SUBMIT: {
                tasks.add((Task) e.getObj());
                mAdapter.notifyItemRangeInserted(e.getArg1(), 1);
                mPlaceholder.setVisibility(View.GONE);
            }
            break;
            case TaskEvent.EVENT_DELETE: {
                tasks.remove(e.getArg1());
                mAdapter.notifyItemRemoved(e.getArg1());
                if (tasks.isEmpty()) {
                    mPlaceholder.setVisibility(View.VISIBLE);
                }
            }
            break;
        }
    }

    private void ensureTaskServiceBound() {
        Validate.requireNotNull(mTaskManager, "bind to task service first");
    }

    private void newTask() {
        ensureTaskServiceBound();
        val task = mTaskManager.newTask();
        // TODO: 2017/2/20 init the task
        task.name = "New Task " + (mAdapter.mTasks.size() + 1);
        task.total = RANDOM.nextInt(191) + 10;
        mTaskManager.submitTask(task);
    }

    private void startTasks(Collection<Task> tasks, boolean start) {
        ensureTaskServiceBound();
        mTaskManager.startTasks(tasks, start);
    }

    private void deleteTasks(Collection<Task> tasks) {
        ensureTaskServiceBound();
        val size = tasks.size();
        if (size == 0) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setMessage(getResources().getQuantityString(R.plurals.delete_task_tip, size, size))
                .setPositiveButton(R.string.ok, (dialog, which) -> mTaskManager.deleteTasks(tasks))
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    private Set<Task> mSelections = new HashSet<>();

    private boolean startSelection() {
        if (mActionMode == null) {
            mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(this);
            updateSelection();
            return true;
        }
        return false;
    }

    private boolean isInSelection() {
        return mActionMode != null;
    }

    private void updateSelection() {
        val size = mSelections.size();
        val menu = mActionMode.getMenu();
        menu.findItem(R.id.action_start).setEnabled(size > 0);
        menu.findItem(R.id.action_pause).setEnabled(size > 0);
        menu.findItem(R.id.action_delete).setEnabled(size > 0);
        mActionMode.setTitle(getResources().getQuantityString(R.plurals.selection_title, size, size));
    }

    private void toggleSelection(Task task, boolean updateUI) {
        task.selected = !task.selected;
        if (task.selected) {
            mSelections.add(task);
        } else {
            mSelections.remove(task);
        }
        if (updateUI) {
            updateSelection();
        }
    }

    private void toggleSelections() {
        if (!isInSelection()) {
            return;
        }
        val tasks = mAdapter.mTasks;
        val taskManager = this.mTaskManager;
        val selected = mSelections.size() != tasks.size();
        Task task;
        for (int i = 0, end = tasks.size(); i < end; i++) {
            task = tasks.get(i);
            task.selected = selected;
            if (selected) {
                mSelections.add(task);
            }
        }
        if (!selected) {
            mSelections.clear();
        }
        updateSelection();
        mAdapter.notifyDataSetChanged();
    }

    private void viewTask(Task task) {
        // todo view details of task
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
        private final LayoutInflater mInflater;
        private final List<Task> mTasks = new ArrayList<>();

        TaskAdapter() {
            mInflater = LayoutInflater.from(TaskFragment.this.getContext());
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
        public void onBindViewHolder(TaskHolder holder, int position, List<Object> payloads) {
            val task = mTasks.get(position);
            if (payloads.isEmpty()) {
                bindData(task, holder);
            } else {
                val o = payloads.get(0);
                if (o instanceof Integer) {
                    bindData(task, holder, (int) o);
                }
            }
            setupListener(holder, task);
        }

        @Override
        public void onBindViewHolder(TaskHolder holder, int position) {
            // ignored by onBindViewHolder(holder, position, payloads)
        }

        private void bindData(Task task, TaskHolder holder) {
            holder.icon.setImageResource(R.mipmap.ic_launcher);
            holder.name.setText(task.name);
            holder.progress.setMax(task.total);
            holder.progress.setProgress(task.progress);
            holder.info.setText(String.format("%d/%d", task.progress, task.total));
            holder.option.setImageResource(optionIconOf(task, isInSelection()));
        }

        private void bindData(Task task, TaskHolder holder, int event) {
            switch (event) {
                case TaskEvent.EVENT_PROGRESS: {
                    holder.progress.setProgress(task.progress);
                    holder.info.setText(String.format("%d/%d", task.progress, task.total));
                }
                break;
                case TaskEvent.EVENT_LIFECYCLE: {
                    holder.option.setImageResource(optionIconOf(task, isInSelection()));
                }
                break;
            }
        }


        private void setupListener(TaskHolder holder, Task task) {
            holder.itemView.setTag(task);
            holder.itemView.setOnClickListener(v -> {
                val it = (Task) v.getTag();
                if (isInSelection()) {
                    toggleSelection(it, true);
                    bindData(it, holder);
                } else {
                    viewTask(it);
                }
            });
            holder.itemView.setOnLongClickListener(v -> !isInSelection() && startSelection());
            holder.option.setTag(task);
            holder.option.setOnClickListener(v -> {
                val it = (Task) v.getTag();
                if (isInSelection()) {
                    toggleSelection(it, true);
                } else {
                    if (it.state == Task.State.Started) {
                        mTaskManager.startTask(task, false);
                    } else if (it.state == Task.State.Paused) {
                        mTaskManager.startTask(task, true);
                    }
                }
                bindData(it, holder);
            });
        }

        private int optionIconOf(Task task, boolean isSelection) {
            if (isSelection) {
                return task.selected
                        ? R.mipmap.ic_checked_checkbox_dark
                        : R.mipmap.ic_unchecked_checkbox_dark;
            }
            switch (task.state) {
                case Started:
                    return R.mipmap.ic_pause_dark;
                case Paused:
                    return R.mipmap.ic_play_dark;
                default:
                    return R.mipmap.ic_view_details_dark;
            }
        }
    }

    static class TaskHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        ImageButton option;
        TextView info;
        ProgressBar progress;

        TaskHolder(View view) {
            super(view);
            icon = viewById(view, R.id.icon);
            name = viewById(view, R.id.name);
            option = viewById(view, R.id.option);
            info = viewById(view, R.id.info);
            progress = viewById(view, R.id.progressBar);
        }
    }
}
