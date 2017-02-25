package pw.phylame.crawling.task;

public class Task {
    enum State {
        Started, Paused, Stopped, Finished, Failed
    }

    /**
     * Name of the task.
     */
    CharSequence name;

    int total;

    int progress = 0;

    volatile State state = State.Paused;

    /**
     * Selection state.
     */
    boolean selected = false;
}
