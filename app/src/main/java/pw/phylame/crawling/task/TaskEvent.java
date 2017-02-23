package pw.phylame.crawling.task;

import android.os.Message;
import android.support.v4.util.Pools;

import lombok.Builder;
import lombok.Data;
import lombok.val;

@Data
@Builder
public class TaskEvent {
    public static final int EVENT_INVALID = -1;
    public static final int EVENT_SUBMIT = 100;
    public static final int EVENT_DELETE = 101;
    public static final int EVENT_PROGRESS = 102;
    public static final int EVENT_CANCELLED = 103;
    public static final int EVENT_LIFECYCLE = 104;

    private static final Pools.SynchronizedPool<TaskEvent> sPool = new Pools.SynchronizedPool<>(16);

    private int type;
    private int arg1;
    private int arg2;
    private Object obj;

    public final void reset() {
        type = EVENT_INVALID;
        arg1 = arg2 = 0;
        obj = null;
    }

    public final Message asMessage() {
        val msg = Message.obtain();
        msg.what = type;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        msg.obj = obj;
        return msg;
    }
}
