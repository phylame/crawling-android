package pw.phylame.crawling.activity;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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

import java.util.ArrayList;
import java.util.List;

import lombok.val;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.crawling.task.Task;
import pw.phylame.support.Activities;
import pw.phylame.support.TimedAction;

import static pw.phylame.support.Views.viewById;

public class CrawlerActivity extends BaseActivity {
    private View mPlaceholder;
    private TaskAdapter mAdapter;
    private ActionMode mActionMode;
    private TimedAction mExitAction;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_crawler, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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

    private void newTask() {
    }

    private void exitApp() {
        CrawlerApp.sharedApp().finish();
        System.exit(0);
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskHolder> {
        private final LayoutInflater mInflater;
        private final List<Task> mTasks = new ArrayList<>();

        private TaskAdapter() {
            mInflater = LayoutInflater.from(CrawlerActivity.this);
        }

        private void addTask(Task task) {
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
                }
            }
//            setupListener(holder, task);
        }

        private void bindData(TaskHolder holder, Task task) {

        }

        private void bindData(TaskHolder holder, Task task, int event) {

        }
    }

    private static class TaskHolder extends RecyclerView.ViewHolder {
        TextView name;
        ImageView icon;
        ImageButton option;
        ProgressBar progress;
        TextView info;

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
