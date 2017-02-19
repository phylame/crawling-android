package pw.phylame.crawling.downloading;

public interface OnTaskProgressListener {
    void onChange(Task task, int position);

    void onDone(Task task, int position);
}
