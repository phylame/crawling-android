package pw.phylame.crawling.model;

import android.os.Binder;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import pw.phylame.commons.function.Functionals;
import pw.phylame.commons.util.Validate;
import pw.phylame.support.RxBus;

@RequiredArgsConstructor
public class TaskBinder extends Binder implements ITaskManager {
    private final ExecutorService mExecutor;
    private final ReentrantLock mLock = new ReentrantLock();
    private final LinkedList<TaskWrapper> mTasks = new LinkedList<>();

    private void submitDirectly(TaskWrapper wrapper) {
        mLock.lock();
        val tasks = this.mTasks;
        try {
            tasks.add(wrapper);
            wrapper.mPosition = tasks.size() - 1;
            wrapper.mState = ITask.State.Submitted;
            RxBus.getDefault().post(TaskEvent.obtain()
                    .type(TaskEvent.EVENT_SUBMIT)
                    .arg1(wrapper.mPosition)
                    .obj(wrapper));
        } finally {
            mLock.unlock();
        }
    }

    private void removeDirectly(TaskWrapper wrapper) {
        mLock.lock();
        val tasks = mTasks;
        try {
            val position = wrapper.mPosition;
            tasks.remove(position);
            for (int i = position, end = tasks.size(); i < end; ++i) {
                --tasks.get(i).mPosition; // decrease position of following tasks
            }
            RxBus.getDefault().post(TaskEvent.obtain()
                    .type(TaskEvent.EVENT_DELETE)
                    .arg1(position)
                    .obj(wrapper));
        } finally {
            mLock.unlock();
        }
    }

    private void checkTask(ITask task) {
        Validate.require(task instanceof TaskWrapper, "task must be created from newTask");
    }

    @Override
    public ITask newTask() {
        return new TaskWrapper();
    }

    @Override
    public void submitTask(@NonNull ITask task) {
        checkTask(task);
        val wrapper = (TaskWrapper) task;
        Validate.require(!mTasks.contains(wrapper), "Task submitted: %s", task);
        mLock.lock();
        try {
            submitDirectly(wrapper);
            wrapper.mFuture = mExecutor.submit(wrapper);
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void deleteTask(@NonNull ITask task) {
        checkTask(task);
        val wrapper = (TaskWrapper) task;
        Validate.require(mTasks.contains(wrapper), "No such task: %s", task);
        mLock.lock();
        try {
            val future = wrapper.mFuture;
            if (future.isDone()) {
                wrapper.mState = ITask.State.Deleted;
            } else if (future.isCancelled() || wrapper.cancel()) {
                wrapper.mState = ITask.State.Cancelled;
            } else {
                throw new IllegalStateException("cannot remove task: " + wrapper);
            }
            removeDirectly(wrapper);
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void deleteTasks(@NonNull Collection<ITask> tasks) {
        mLock.lock();
        try {
            Functionals.foreach(tasks.iterator(), this::deleteTask);
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void startTask(@NonNull ITask task, boolean start) {
        checkTask(task);
        val wrapper = (TaskWrapper) task;
        Validate.require(mTasks.contains(wrapper), "No such task: %s", task);
        mLock.lock();
        try {
            if (start) {
                wrapper.pause();
            } else {
                wrapper.start();
            }
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void startTasks(@NonNull Collection<ITask> tasks, boolean start) {
        mLock.lock();
        try {
            Functionals.foreach(tasks.iterator(), task -> startTask(task, start));
        } finally {
            mLock.unlock();
        }
    }
}
