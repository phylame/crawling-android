package pw.phylame.crawling.task;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import pw.phylame.commons.function.Functionals;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.R;
import pw.phylame.support.RxBus;

public class TaskService extends Service {
    /**
     * The global event bus.
     */
    private static final RxBus sBus = RxBus.getDefault();

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
        private final ReentrantLock mLock = new ReentrantLock();

        @Override
        public int getCount() {
            return mTasks.size();
        }

        @Override
        public Task getTask(int index) {
            return mTasks.get(index).mTask;
        }

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

        private void removeDirectly(int position) {
            mTasks.remove(position);
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
                wrapper.mPosition = tasks.size() - 1;
                wrapper.mFuture = mExecutor.get().submit(wrapper);
                tasks.add(wrapper);

                sBus.post(TaskEvent.builder()
                        .type(TaskEvent.EVENT_SUBMIT)
                        .arg1(wrapper.mPosition)
                        .obj(task)
                        .build());
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

                sBus.post(TaskEvent.builder()
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
                int position = pair.second;
                tasks.remove(position);
                for (int i = position + 1, end = tasks.size(); i < end; ++i) {
                    --tasks.get(i).mPosition; // increase position of following tasks
                }

                sBus.post(TaskEvent.builder()
                        .type(TaskEvent.EVENT_DELETE)
                        .arg1(position)
                        .obj(task)
                        .build());
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
            sBus.post(event);

            for (int i = 1, end = task.total + 1; i != end; ++i) {
                while (task.state == Task.State.Paused) { // task is paused
                    // TODO: 2017-2-22 cache task and finish for releasing thread
                    Thread.yield();
                }
                try {
                    Thread.sleep(RANDOM.nextInt(100));
                    // TODO: 2017-2-22 fetching task

                    task.progress = i;
                    event.setType(TaskEvent.EVENT_PROGRESS);
                    event.setArg2(task.total);
                    event.setArg1(i);
                    sBus.post(event);
                } catch (InterruptedException e) { // task is cancelled
                    Log.d(TAG, "task is cancelled");
                    cleanup();

                    task.state = Task.State.Stopped;
                    event.setType(TaskEvent.EVENT_CANCELLED);
                    event.setArg1(mPosition);
                    event.setObj(task);
                    sBus.post(event);
                    return;
                }
            }
            mTaskManager.removeDirectly(mPosition); // remove from task list
            task.state = Task.State.Finished;
            event.setType(TaskEvent.EVENT_LIFECYCLE);
            event.setArg1(mPosition);
            event.setObj(task);
            sBus.post(event);
        }

        private void cleanup() {
            // TODO: 2017-2-22 cleanup when task is cancelled
        }
    }
}
