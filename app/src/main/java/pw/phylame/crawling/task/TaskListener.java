package pw.phylame.crawling.task;

public interface TaskListener {
    void onStart(Task task);

    void onDone(Task task);

    void onDelete(Task task, int position);

    void onChange(Task task);
}
