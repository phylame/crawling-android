package pw.phylame.crawling.model;

import android.support.v4.util.Pools;

import lombok.val;

public class TaskEvent {
    public static final int EVENT_SUBMIT = 100;
    public static final int EVENT_DELETE = 101;
    public static final int EVENT_PROGRESS = 102;
    public static final int EVENT_CANCELLED = 103;
    public static final int EVENT_LIFECYCLE = 104;

    public int type;
    public int arg1;
    public int arg2;
    public Object obj;

    public final TaskEvent type(int type) {
        this.type = type;
        return this;
    }

    public final TaskEvent arg1(int arg1) {
        this.arg1 = arg1;
        return this;
    }

    public final TaskEvent arg2(int arg2) {
        this.arg2 = arg2;
        return this;
    }

    public final TaskEvent obj(Object obj) {
        this.obj = obj;
        return this;
    }

    public final void recycle() {
        sPool.release(this);
    }

    private static final Pools.SynchronizedPool<TaskEvent> sPool = new Pools.SynchronizedPool<>(16);

    public static TaskEvent obtain() {
        val instance = sPool.acquire();
        return (instance != null) ? instance : new TaskEvent();
    }
}
