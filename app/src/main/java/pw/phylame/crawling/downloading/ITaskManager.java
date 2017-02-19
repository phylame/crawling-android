package pw.phylame.crawling.downloading;

import java.util.Collection;
import java.util.List;

interface ITaskManager {
    int getCount();

    List<Task> getTasks();

    Task getTask(int index);

    boolean newTask(Task task);

    void deleteTask(Task task);

    void startTask(Task task, boolean start);

    void startTasks(Collection<Task> tasks, boolean start);

    boolean deleteTasks(Collection<Task> tasks);

    void setOnProgressChangeListener(OnTaskProgressListener l);
}
