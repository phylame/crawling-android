package pw.phylame.crawling.task;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.val;
import pw.phylame.commons.function.Functionals;
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
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mExecutor.isInitialized()) {
            mExecutor.get().shutdown();
        }
    }

    private class TaskManagerBinder extends Binder implements ITaskManager {
        private final List<TaskWrapper> mTasks = new ArrayList<>();
        private final ReentrantLock mLock = new ReentrantLock();

        private Pair<TaskWrapper, Integer> findWrapper(Task task) {
            val tasks = mTasks;
            TaskWrapper wrapper;
            for (int i = 0, end = tasks.size(); i < end; ++i) {
                wrapper = tasks.get(i);
                if (wrapper.mTask == task) {
                    return Pair.create(wrapper, i);
                }
            }
            return null;
        }

        private boolean contains(Task task) {
            return findWrapper(task) != null;
        }

        private void submitDirectly(TaskWrapper wrapper) {
            mLock.lock();
            val tasks = this.mTasks;
            try {
                wrapper.mPosition = tasks.size();
                tasks.add(wrapper);
                RxBus.getDefault().post(TaskEvent.builder()
                        .type(TaskEvent.EVENT_SUBMIT)
                        .arg1(wrapper.mPosition)
                        .obj(wrapper.mTask)
                        .build());
            } finally {
                mLock.unlock();
            }
        }

        private void removeDirectly(int position) {
            mLock.lock();
            val tasks = this.mTasks;
            try {
                val wrapper = tasks.remove(position);
                for (int i = position, end = tasks.size(); i < end; ++i) {
                    --tasks.get(i).mPosition; // decrease position of following tasks
                }
                RxBus.getDefault().post(TaskEvent.builder()
                        .type(TaskEvent.EVENT_DELETE)
                        .obj(wrapper.mTask)
                        .arg1(position)
                        .build());
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public Task newTask() {
            return new Task();
        }

        @Override
        public void submitTask(@NonNull Task task) {
            if (contains(task)) {
                return;
            }
            mLock.lock();
            val tasks = this.mTasks;
            try {
                val wrapper = new TaskWrapper(task, this);
                submitDirectly(wrapper);
                wrapper.mFuture = mExecutor.get().submit(wrapper);
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void startTask(@NonNull Task task, boolean start) {
            val pair = findWrapper(task);
            if (pair == null) {
                throw new IllegalArgumentException("No such task " + task);
            }
            mLock.lock();
            try {
                task.state = start ? Task.State.Started : Task.State.Paused;
                RxBus.getDefault().post(TaskEvent.builder()
                        .type(TaskEvent.EVENT_LIFECYCLE)
                        .arg1(pair.first.mPosition)
                        .obj(task)
                        .build());
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void startTasks(@NonNull Collection<Task> tasks, boolean start) {
            mLock.lock();
            try {
                Functionals.foreach(tasks.iterator(), task -> startTask(task, start));
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void deleteTask(@NonNull Task task) {
            val pair = findWrapper(task);
            if (pair == null) {
                throw new IllegalArgumentException("No such task " + task);
            }
            mLock.lock();
            val tasks = this.mTasks;
            try {
                val wrapper = pair.first;
                // try cancel undone and uncanceled task
                if (!wrapper.cancelIfNeed()) {
                    return;
                }
                removeDirectly(pair.second);
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void deleteTasks(@NonNull Collection<Task> tasks) {
            mLock.lock();
            try {
                Functionals.foreach(tasks.iterator(), this::deleteTask);
            } finally {
                mLock.unlock();
            }
        }
    }

    @ToString
    @RequiredArgsConstructor
    private static class TaskWrapper implements Runnable {
        private static final String TAG = TaskWrapper.class.getSimpleName();

        private final Task mTask;
        private final TaskManagerBinder mTaskManager;

        private int mPosition;
        private Future<?> mFuture;

        boolean cancelIfNeed() {
            return mFuture.isDone() || mFuture.isCancelled() || mFuture.cancel(true);
        }

        private static final Random RANDOM = new Random();

        @Override
        public void run() {
            val task = this.mTask;
            task.state = Task.State.Started;

            // reused event object
            val event = TaskEvent.builder()
                    .type(TaskEvent.EVENT_LIFECYCLE)
                    .arg1(mPosition)
                    .obj(task)
                    .build();
            RxBus.getDefault().post(event);

            for (int i = 1, end = task.total + 1; i != end; ++i) {
                while (task.state == Task.State.Paused) { // task is paused
                    // TODO: 2017-2-22 cache task and finish for releasing thread
                    Thread.yield();
                }
                try {
                    Thread.sleep(RANDOM.nextInt(50));
                    // TODO: 2017-2-22 fetching task

                    task.progress = i;
                    event.reset();
                    event.setType(TaskEvent.EVENT_PROGRESS);
                    event.setArg1(mPosition);
                    event.setObj(mTask);
                    event.setArg2(i);
                    RxBus.getDefault().post(event);
                } catch (InterruptedException e) { // task is cancelled
                    Log.d(TAG, "task is cancelled");
                    cleanup();

                    task.state = Task.State.Stopped;
                    event.reset();
                    event.setType(TaskEvent.EVENT_CANCELLED);
                    event.setArg1(mPosition);
                    event.setObj(task);
                    RxBus.getDefault().post(event);
                    return;
                }
            }
            mTaskManager.removeDirectly(mPosition); // remove from task list
        }

        private void cleanup() {
            // TODO: 2017-2-22 cleanup when task is cancelled
        }
    }
}
