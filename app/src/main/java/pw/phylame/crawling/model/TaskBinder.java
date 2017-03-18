package pw.phylame.crawling.model;

import android.os.Binder;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import jem.epm.EpmManager;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import pw.phylame.commons.function.Functionals;
import pw.phylame.commons.util.Validate;

@RequiredArgsConstructor
public class TaskBinder extends Binder implements ITaskManager {
    private final ExecutorService mExecutor;
    private final ReentrantLock mLock = new ReentrantLock();
    private final List<TaskWrapper> mTasks = new LinkedList<>();

    public final void cleanup() {
    }

    private void appendTask0(TaskWrapper wrapper) {
        mLock.lock();
        val tasks = this.mTasks;
        try {
            tasks.add(wrapper);
            wrapper.mPosition = tasks.size() - 1;
            ITask.postMessage(ITask.EVENT_SUBMIT, wrapper.mPosition, wrapper);
            wrapper.setState(ITask.State.Submitted);
        } finally {
            mLock.unlock();
        }
    }

    private void removeTask0(TaskWrapper wrapper) {
        mLock.lock();
        val tasks = mTasks;
        try {
            val position = wrapper.mPosition;
            if (tasks.remove(position) != wrapper) {
                throw new IllegalStateException("Bad task position: " + wrapper);
            }
            for (int i = position, end = tasks.size(); i < end; ++i) {
                --tasks.get(i).mPosition; // decrease position of following tasks
            }
            ITask.postMessage(ITask.EVENT_DELETE, position, wrapper);
        } finally {
            mLock.unlock();
        }
    }

    private TaskWrapper checkType(ITask task) {
        if (task instanceof TaskWrapper) {
            return (TaskWrapper) task;
        }
        throw new IllegalArgumentException("Task must be created from newTask()");
    }

    private void ensureSubmitted(TaskWrapper wrapper) {
        Validate.require(mTasks.contains(wrapper), "No such task: %s", wrapper);
    }

    @Override
    public ITask newTask() {
        return new TaskWrapper(this);
    }

    @Override
    public List<? extends ITask> getTasks() {
        return Collections.unmodifiableList(mTasks);
    }

    @Override
    public void submitTask(@NonNull ITask task) {
        val wrapper = checkType(task);
        Validate.require(wrapper.getBook() != null || wrapper.getUrl() != null, "No book or URL specified");
        Validate.requireNotNull(wrapper.getOutput(), "No output specified");
        Validate.requireNotEmpty(wrapper.getFormat(), "Format is null or empty");
        Validate.require(EpmManager.hasMaker(wrapper.getFormat()), "Unsupported format: %s", wrapper.getFormat());
        mLock.lock();
        try {
            Validate.require(!mTasks.contains(wrapper), "Task is already submitted: %s", wrapper);
            appendTask0(wrapper);
            scheduleTask(wrapper);
        } finally {
            mLock.unlock();
        }
    }

    final void scheduleTask(TaskWrapper wrapper) {
        wrapper.mFuture = mExecutor.submit(wrapper);
    }

    @Override
    public void deleteTask(@NonNull ITask task) {
        val wrapper = checkType(task);
        mLock.lock();
        try {
            ensureSubmitted(wrapper);
            if (!wrapper.cancel()) {
                throw new IllegalStateException("Cannot cancel task: " + wrapper);
            }
            removeTask0(wrapper);
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void deleteTasks(@NonNull Collection<ITask> tasks) {
        mLock.lock();
        try {
            for (val task : tasks.toArray(new ITask[tasks.size()])) {
                deleteTask(task);
            }
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void startTask(@NonNull ITask task, boolean start) {
        val wrapper = checkType(task);
        mLock.lock();
        try {
            ensureSubmitted(wrapper);
            if (start) {
                if (wrapper.getState() == ITask.State.Paused) {
                    wrapper.start();
                }
            } else {
                if (wrapper.getState() == ITask.State.Started) {
                    wrapper.pause();
                }
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
