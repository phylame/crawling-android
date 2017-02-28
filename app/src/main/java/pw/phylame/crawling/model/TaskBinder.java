package pw.phylame.crawling.model;

import android.os.Binder;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import jem.epm.EpmManager;
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
            wrapper.mBinder = new WeakReference<>(this);
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
    public List<? extends ITask> tasks() {
        return Collections.unmodifiableList(mTasks);
    }

    @Override
    public void submitTask(@NonNull ITask task) {
        checkTask(task);
        val wrapper = (TaskWrapper) task;
        Validate.requireNotNull(wrapper.getBook(), "No book specified");
        Validate.requireNotNull(wrapper.getOutput(), "No output specified");
        Validate.requireNotEmpty(wrapper.getFormat(), "Format is null or empty");
        Validate.require(EpmManager.hasMaker(wrapper.getFormat()), "Unsupported format: %s", wrapper.getFormat());
        Validate.require(!mTasks.contains(wrapper), "Task submitted: %s", task);
        mLock.lock();
        try {
            submitDirectly(wrapper);
            submitTask0(wrapper);
        } finally {
            mLock.unlock();
        }
    }

    void submitTask0(TaskWrapper wrapper) {
        wrapper.mFuture = mExecutor.submit(wrapper);
    }

    @Override
    public void deleteTask(@NonNull ITask task) {
        checkTask(task);
        val wrapper = (TaskWrapper) task;
        Validate.require(mTasks.contains(wrapper), "No such task: %s", task);
        mLock.lock();
        try {
            deleteTask0(wrapper);
        } finally {
            mLock.unlock();
        }
    }

    private void deleteTask0(TaskWrapper wrapper) {
        val future = wrapper.mFuture;
        if (future.isDone()) {
            wrapper.mState = ITask.State.Deleted;
        } else if (future.isCancelled() || wrapper.cancel()) {
            wrapper.mState = ITask.State.Cancelled;
        } else {
            throw new IllegalStateException("cannot remove task: " + wrapper);
        }
        removeDirectly(wrapper);
    }

    @Override
    public void deleteTasks(@NonNull Collection<ITask> tasks) {
        mLock.lock();
        try {
            for (val task : tasks.toArray(new ITask[tasks.size()])) {
                deleteTask0((TaskWrapper) task);
            }
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
                wrapper.start();
            } else {
                wrapper.pause();
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
