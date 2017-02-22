package pw.phylame.crawling.task;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskEvent {
    public static final int EVENT_SUBMIT = 100;
    public static final int EVENT_DELETE = 101;
    public static final int EVENT_PROGRESS = 102;
    public static final int EVENT_CANCELLED = 103;
    public static final int EVENT_LIFECYCLE = 104;

    private int type;
    private int arg1;
    private int arg2;
    private Object obj;
}
