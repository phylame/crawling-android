package pw.phylame.crawling.model;

import java.util.Collection;

public interface ITaskManager {
    /**
     * Creates an new task.
     *
     * @return the task
     */
    ITask newTask();

    /**
     * Submits a task for processing.
     *
     * @param task the task
     */
    void submitTask(ITask task);

    /**
     * Deletes specified task.
     * <p>The task will be cancelled if is processing.</p>
     *
     * @param task the task
     */
    void deleteTask(ITask task);

    /**
     * Deletes all specified tasks.
     *
     * @param tasks the tasks
     */
    void deleteTasks(Collection<ITask> tasks);

    /**
     * Sets state of specified task.
     *
     * @param task  the task
     * @param start {@literal true} for starting, {@literal false} for pausing
     */
    void startTask(ITask task, boolean start);

    /**
     * Sets state of all specified tasks.
     *
     * @param tasks the task
     * @param start {@literal true} for starting, {@literal false} for pausing
     */
    void startTasks(Collection<ITask> tasks, boolean start);
}
