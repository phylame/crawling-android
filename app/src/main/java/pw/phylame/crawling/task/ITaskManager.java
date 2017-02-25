package pw.phylame.crawling.task;

import java.util.Collection;

/**
 * Interface provided task management.
 */
public interface ITaskManager {
    /**
     * Creates an new task.
     *
     * @return the task
     */
    Task newTask();

    /**
     * Submits a task for processing.
     *
     * @param task the task
     */
    void submitTask(Task task);

    /**
     * Deletes specified task.
     * <p>The task will be cancelled if is processing.</p>
     *
     * @param task the task
     */
    void deleteTask(Task task);

    /**
     * Deletes all specified tasks.
     *
     * @param tasks the tasks
     */
    void deleteTasks(Collection<Task> tasks);

    /**
     * Sets state of specified task.
     *
     * @param task  the task
     * @param start {@literal true} for starting, {@literal false} for pausing
     */
    void startTask(Task task, boolean start);

    /**
     * Sets state of all specified tasks.
     *
     * @param tasks the task
     * @param start {@literal true} for starting, {@literal false} for pausing
     */
    void startTasks(Collection<Task> tasks, boolean start);
}
