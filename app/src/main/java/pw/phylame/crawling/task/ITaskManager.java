package pw.phylame.crawling.task;

import java.util.Collection;

/**
 * Interface provided task management.
 */
interface ITaskManager {
    /**
     * Returns the number of tasks,
     *
     * @return number of task
     */
    int getCount();

    /**
     * Gets task with specified index.
     *
     * @param index the index of task
     * @return the task
     */
    Task getTask(int index);

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
     * @return {@literal true} if success, {@literal false} if already submitted
     */
    boolean submitTask(Task task);

    /**
     * Deletes specified task.
     * <p>The task will be cancelled if is processing.</p>
     *
     * @param task the task
     * @return {@literal true} if task deleted, otherwise {@literal false}
     */
    boolean deleteTask(Task task);

    /**
     * Deletes all specified tasks.
     *
     * @param tasks the tasks
     * @return {@literal true} if all tasks deleted, otherwise {@literal false}
     */
    boolean deleteTasks(Collection<Task> tasks);

    /**
     * Sets state of specified task.
     *
     * @param task  the task
     * @param start {@literal true} for starting, {@literal false} for pausing
     * @return {@literal true} if success, otherwise {@literal false}
     */
    boolean startTask(Task task, boolean start);

    /**
     * Sets state of all specified tasks.
     *
     * @param tasks the task
     * @param start {@literal true} for starting, {@literal false} for pausing
     * @return {@literal true} if all tasks processed, otherwise {@literal false}
     */
    boolean startTasks(Collection<Task> tasks, boolean start);

    /**
     * Registers listener for task state changing.
     *
     * @param l the listener
     */
    void setTaskListener(TaskListener l);
}
