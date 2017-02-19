package pw.phylame.crawling.downloading;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import lombok.val;
import pw.phylame.crawling.R;
import pw.phylame.support.VerticalDivider;

import static pw.phylame.support.Views.viewById;

public class DownloadingFragment extends Fragment implements ActionMode.Callback, OnTaskProgressListener {
    private TextView mEmptyTip;
    private TaskAdapter mAdapter;
    private RecyclerView mRecycler;
    private ActionMode mActionMode;

    public DownloadingFragment() {
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment DownloadingFragment.
     */
    public static DownloadingFragment newInstance() {
        return new DownloadingFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mTaskManager == null) {
            val intent = new Intent(getContext(), FetchService.class);
            getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        if (mAdapter == null) {
            mRecycler.setAdapter(mAdapter = new TaskAdapter());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTaskManager != null) {
            getActivity().unbindService(mConnection);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        val view = inflater.inflate(R.layout.fragment_downloading, container, false);
        mEmptyTip = viewById(view, R.id.empty_tip);
        mRecycler = viewById(view, R.id.recycler);
        mRecycler.setItemAnimator(new DefaultItemAnimator());
        mRecycler.addItemDecoration(new VerticalDivider(getActivity()));
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_downloading, menu);
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
        mode.getMenuInflater().inflate(R.menu.menu_task, menu);
        mAdapter.notifyDataSetChanged();
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
                toggleTasks();
                break;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        mSelections.clear();
        mAdapter.notifyDataSetChanged();
    }

    private ITaskManager mTaskManager;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            System.out.println("DownloadingFragment.onServiceConnected");
            mTaskManager = (ITaskManager) service;
            mTaskManager.setOnProgressChangeListener(DownloadingFragment.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            System.out.println("DownloadingFragment.onServiceDisconnected");
            mTaskManager.setOnProgressChangeListener(null);
            mTaskManager = null;
        }
    };

    public static final int UPDATE_PROGRESS = 100;
    public static final int TASK_DONE = 101;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_PROGRESS:
                    mAdapter.notifyItemChanged(msg.arg1);
                    break;
                case TASK_DONE:
                    mAdapter.notifyItemRemoved(msg.arg1);
                    break;
            }
        }
    };

    @Override
    public void onChange(Task task, int position) {
        val msg = Message.obtain();
        msg.what = UPDATE_PROGRESS;
        msg.arg1 = position;
        mHandler.sendMessage(msg);
    }

    @Override
    public void onDone(Task task, int position) {
        val msg = Message.obtain();
        msg.what = TASK_DONE;
        msg.arg1 = position;
        mHandler.sendMessage(msg);
    }

    Random random = new Random();

    private void newTask() {
        val task = new Task("New Task");
        task.total = random.nextInt(491) + 10;
        if (mTaskManager.newTask(task)) {
            mEmptyTip.setVisibility(View.GONE);
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
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mTaskManager.deleteTasks(tasks)) {
                            mSelections.clear();
                            updateSelection();
                            mAdapter.notifyDataSetChanged();
                            if (mTaskManager.getCount() == 0) {
                                mActionMode.finish();
                                mEmptyTip.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private Set<Task> mSelections = new HashSet<>();

    private boolean startSelection() {
        if (mActionMode == null) {
            mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(DownloadingFragment.this);
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

    private void toggleTask(Task task, boolean updateUI) {
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

    private void toggleTasks() {
        if (!isInSelection()) {
            return;
        }
        val tasks = mTaskManager.getTasks();
        val selected = mSelections.size() != tasks.size();
        for (Task task : tasks) {
            task.selected = selected;
        }
        if (selected) {
            mSelections.addAll(tasks);
        } else {
            mSelections.clear();
        }
        updateSelection();
        mAdapter.notifyDataSetChanged();
    }

    private void viewTask(Task task) {

    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
        private final LayoutInflater inflater;

        TaskAdapter() {
            inflater = LayoutInflater.from(getContext());
        }

        @Override
        public TaskHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            val view = inflater.inflate(R.layout.task_item, parent, false);
            return new TaskHolder(view);
        }

        @Override
        public void onBindViewHolder(final TaskHolder holder, int position) {
            final val task = mTaskManager.getTask(position);
            holder.bindData(task);
            holder.itemView.setTag(task);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    val task = (Task) v.getTag();
                    if (isInSelection()) {
                        toggleTask(task, true);
                        holder.bindData(task);
                    } else {
                        viewTask(task);
                    }
                }
            });
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return startSelection();
                }
            });
            holder.option.setTag(task);
            holder.option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    val task = (Task) v.getTag();
                    if (isInSelection()) {
                        toggleTask(task, true);
                    } else {
                        if (task.state == Task.State.Started) {
                            task.state = Task.State.Paused;
                        } else if (task.state == Task.State.Paused) {
                            task.state = Task.State.Started;
                        }
                    }
                    holder.bindData(task);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mTaskManager == null ? 0 : mTaskManager.getCount();
        }
    }

    class TaskHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final ImageButton option;
        final TextView info;
        final ProgressBar progress;

        TaskHolder(View view) {
            super(view);
            icon = viewById(view, R.id.icon);
            name = viewById(view, R.id.name);
            option = viewById(view, R.id.option);
            info = viewById(view, R.id.info);
            progress = viewById(view, R.id.progressBar);
        }

        int optionIconOf(Task task) {
            if (isInSelection()) {
                return task.selected
                        ? R.mipmap.ic_check_on
                        : R.mipmap.ic_check_off;
            }
            switch (task.state) {
                case Started:
                    return R.mipmap.ic_pause_circle;
                case Paused:
                    return R.mipmap.ic_play_circle;
                default:
                    return R.mipmap.ic_open_book;
            }
        }

        void bindData(Task task) {
            icon.setImageResource(R.mipmap.ic_launcher);
            name.setText(task.name);
            progress.setMax(task.total);
            progress.setProgress(task.progress);
            info.setText(String.format("%d/%d", task.progress, task.total));
            option.setImageResource(optionIconOf(task));
        }
    }
}
