package pw.phylame.crawling.task;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import lombok.val;
import pw.phylame.crawling.R;
import pw.phylame.support.RxBus;

import static pw.phylame.support.Views.viewById;

public class TaskFragment extends Fragment implements ActionMode.Callback, ServiceConnection {
    private View mPlaceholder;
    private UIHandler mHandler;
    private TaskAdapter mAdapter;
    private ActionMode mActionMode;
    private ITaskManager mTaskManager;

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
        mHandler = new UIHandler(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        System.out.println(mTaskManager);
        if (mTaskManager == null) {
            val context = getContext();
            context.bindService(new Intent(context, TaskService.class), this, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTaskManager != null) {
//            getContext().unbindService(this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        val view = inflater.inflate(R.layout.fragment_task, container, false);
        mPlaceholder = view.findViewById(R.id.placeholder);
        initRecycler(view);
        return view;
    }

    private void initRecycler(View view) {
        mAdapter = new TaskAdapter(this);
        RecyclerView recycler = viewById(view, R.id.recycler);
        recycler.setAdapter(mAdapter);
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(new DefaultItemAnimator());
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
        mAdapter.notifyDataSetChanged(); // refresh selection state
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
        mTaskManager = (ITaskManager) service;
//        RxBus.getDefault().subscribe(TaskProgressEvent.class, System.out::println);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mTaskManager = null;
    }

    private static final Random RANDOM = new Random();

    private void newTask() {
        val task = mTaskManager.newTask();
        // TODO: 2017/2/20 init the task
        task.name = "New Task " + (mTaskManager.getCount() + 1);
        task.total = RANDOM.nextInt(491) + 10;

        if (mTaskManager.submitTask(task)) {
            mPlaceholder.setVisibility(View.GONE);
            mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
        }
    }

    private void startTasks(Collection<Task> tasks, boolean start) {
        mTaskManager.startTasks(tasks, start);
    }

    private void deleteTasks(final Collection<Task> tasks) {
        val size = tasks.size();
        if (size == 0) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setMessage(getResources().getQuantityString(R.plurals.delete_task_tip, size, size))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (mTaskManager.deleteTasks(tasks)) {
                        mSelections.clear();
                        updateSelection();
                        mAdapter.notifyDataSetChanged();
                        if (mTaskManager.getCount() == 0) {
                            mActionMode.finish();
                            mPlaceholder.setVisibility(View.VISIBLE);
                        }
                    }
                })
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
        val selected = mSelections.size() != mTaskManager.getCount();
        for (int i = 0, end = mTaskManager.getCount(); i < end; i++) {
            mTaskManager.getTask(i).selected = selected;
            if (selected) {
                mSelections.add(mTaskManager.getTask(i));
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

    private static class UIHandler extends Handler {
        static final int UPDATE_TASK = 1;

        private final WeakReference<TaskFragment> mInvoker;

        UIHandler(TaskFragment fragment) {
            this.mInvoker = new WeakReference<TaskFragment>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            val fragment = mInvoker.get();
            switch (msg.what) {
                case UPDATE_TASK: {
                    val task = (Task) msg.obj;
                    if (task.mHolder != null) {
                        fragment.mAdapter.bindData(task, task.mHolder);
                    }
                }
                break;
                default:
                    break;
            }
        }
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
        private final LayoutInflater mInflater;

        TaskAdapter(TaskFragment fragment) {
            mInflater = LayoutInflater.from(fragment.getContext());
        }

        @Override
        public int getItemCount() {
            return mTaskManager == null ? 0 : mTaskManager.getCount();
        }

        @Override
        public TaskHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new TaskHolder(mInflater.inflate(R.layout.task_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final TaskHolder holder, int position) {
            val task = mTaskManager.getTask(position);
            bindData(task, holder);
            task.mHolder = holder;
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

        private void bindData(Task task, TaskHolder holder) {
            holder.icon.setImageResource(R.mipmap.ic_launcher);
            holder.name.setText(task.name);
            holder.progress.setMax(task.total);
            holder.progress.setProgress(task.progress);
            holder.info.setText(String.format("%d/%d", task.progress, task.total));
            holder.option.setImageResource(optionIconOf(task, isInSelection()));
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
