package pw.phylame.crawling.model;

public interface ITask {
    enum State {
        Submitted, Started, Paused, Cancelled, Deleted
    }

    State getState();
}
