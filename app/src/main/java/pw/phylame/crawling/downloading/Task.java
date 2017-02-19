package pw.phylame.crawling.downloading;

class Task {
    enum State {
        Started, Paused, Stopped, Finished, Failed
    }

    String name;

    int total;
    int progress = 0;

    State state = State.Paused;

    boolean selected;

    enum ChangeSource {
        All, Progress
    }

    ChangeSource changeSource = ChangeSource.All;

    Task(String name) {
        this.name = name;
    }
}
