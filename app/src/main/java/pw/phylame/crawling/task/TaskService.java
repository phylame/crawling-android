package pw.phylame.crawling.task;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.NonNull;
import lombok.val;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.R;
import pw.phylame.support.RxBus;

public class TaskService extends Service {
    private final TaskManagerBinder mBinder = new TaskManagerBinder();

    private final Lazy<ExecutorService> mExecutor = new Lazy<ExecutorService>(() -> {
        val count = Math.max(getResources().getInteger(R.integer.init_task_limit), Runtime.getRuntime().availableProcessors() * 2);
        return Executors.newFixedThreadPool(count);
    });

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mExecutor.isInitialized()) {
            mExecutor.get().shutdown();
        }
    }

    private class TaskManagerBinder extends Binder implements ITaskManager {
        private final List<TaskWrapper> mTasks = new Vector<>();

        @Override
        public int getCount() {
            return mTasks.size();
        }

        @Override
        public Task getTask(int index) {
            return mTasks.get(index).mTask;
        }

        private Pair<TaskWrapper, Integer> wrapperFor(Task task) {
            if (task == null) {
                return null;
            }
            TaskWrapper wrapper;
            for (int i = 0, end = mTasks.size(); i < end; ++i) {
                wrapper = mTasks.get(i);
                if (wrapper.mTask == task) {
                    return Pair.create(wrapper, i);
                }
            }
            return null;
        }

        private boolean contains(Task task) {
            return wrapperFor(task) != null;
        }

        @Override
        public Task newTask() {
            return new Task();
        }

        @Override
        public boolean submitTask(@NonNull Task task) {
            if (contains(task)) {
                return false;
            }
            val wrapper = new TaskWrapper(task);
            wrapper.mPosition = mTasks.size();
            wrapper.mService = TaskService.this;
            wrapper.mFuture = mExecutor.get().submit(wrapper);
            mTasks.add(wrapper);
            return true;
        }

        @Override
        public boolean startTask(@NonNull Task task, boolean start) {
            val pair = wrapperFor(task);
            if (pair == null) {
                return false;
            }
            task.state = start ? Task.State.Started : Task.State.Paused;
            // TODO: 2017-2-21 post event to bus
            return true;
        }

        @Override
        public boolean startTasks(@NonNull Collection<Task> tasks, boolean start) {
            if (tasks.isEmpty()) {
                return true;
            }
            boolean result = true;
            for (val task : tasks) {
                if (!startTask(task, start)) {
                    result = false;
                }
            }
            return result;
        }

        @Override
        public boolean deleteTask(@NonNull Task task) {
            val pair = wrapperFor(task);
            if (pair == null) {
                return false;
            }
            val wrapper = pair.first;
            if (!wrapper.mFuture.isDone() && !wrapper.mFuture.isCancelled() && !wrapper.mFuture.cancel(true)) { // failed to cancel
                return false;
            }
            mTasks.remove((int) pair.second);
            // TODO: 2017-2-21 post event to bus
            return true;
        }

        @Override
        public boolean deleteTasks(@NonNull Collection<Task> tasks) {
            if (tasks.isEmpty()) {
                return true;
            }
            boolean result = true;
            for (val task : tasks) {
                if (!deleteTask(task)) {
                    result = false;
                }
            }
            return result;
        }
    }

    private static class TaskWrapper implements Runnable {
        private int mPosition;
        private final Task mTask;
        private Future<?> mFuture;
        private TaskService mService;

        TaskWrapper(Task task) {
            mTask = task;
        }

        private static final Random RANDOM = new Random();

        @Override
        public void run() {
            mTask.state = Task.State.Started;
            // TODO: 2017-2-21 post event to bus
            for (int i = 1, end = mTask.total + 1; i != end; ++i) {
                while (mTask.state == Task.State.Paused) {
                    Thread.yield();
                }
                try {
                    Thread.sleep(RANDOM.nextInt(100));
                    mTask.progress = i;
                    RxBus.getDefault().post(new TaskProgressEvent(i, mTask.total));
                } catch (InterruptedException e) {
                    System.out.println("cancel task " + mTask);
                    mTask.state = Task.State.Stopped;
                    return;
                }
            }
            val position = mService.mBinder.mTasks.indexOf(this);
            mService.mBinder.mTasks.remove(position);
            mTask.state = Task.State.Finished;
            // TODO: 2017-2-21 post event to bus
        }
    }
}
