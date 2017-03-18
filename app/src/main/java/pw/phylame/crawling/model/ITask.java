package pw.phylame.crawling.model;

import android.graphics.drawable.Drawable;
import android.os.Message;

import java.io.File;

import jem.crawler.CrawlerBook;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.val;
import pw.phylame.support.DataHub;
import pw.phylame.support.RxBus;

@Getter
public abstract class ITask {
    /**
     * State of task is submitted.
     */
    public static final int EVENT_SUBMIT = 100;
    /**
     * State of task is updated.
     */
    public static final int EVENT_STATE = 101;
    /**
     * Task is deleted.
     */
    public static final int EVENT_DELETE = 102;
    /**
     * Attributes and contents of book for URL is fetched.
     */
    public static final int EVENT_FETCHED = 103;
    /**
     * Task progress is updated.
     */
    public static final int EVENT_PROGRESS = 104;

    public static void postMessage(int type, int arg1, int arg2) {
        val msg = Message.obtain();
        msg.what = type;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        RxBus.getDefault().post(msg);
    }

    public static void postMessage(int type, int arg1, Object data) {
        val msg = Message.obtain();
        msg.what = type;
        msg.arg1 = arg1;
        if (data != null) {
            msg.arg2 = data.hashCode();
            DataHub.put(msg.arg2, data);
        }
        RxBus.getDefault().post(msg);
    }

    public enum State {
        /**
         * Task is submitted, but not started.
         */
        Submitted,
        /**
         * Task is running for fetching attributes, contents and texts.
         */
        Started,
        /**
         * Task is paused by user.
         */
        Paused,
        /**
         * All done.
         */
        Finished,
        /**
         * An expected error is occurred when running.
         */
        Failed
    }

    /**
     * The book.
     */
    @NonNull
    @Setter(AccessLevel.PROTECTED)
    private CrawlerBook book;
    /**
     * URL of the book attributes page.
     */
    @NonNull
    private String url;
    /**
     * Output for storing book.
     */
    @NonNull
    private File output;
    /**
     * Output format.
     */
    @NonNull
    private String format;
    /**
     * Backup book as PMAB.
     */
    private boolean backup;
    /**
     * Cover thumbnail.
     */
    @Setter
    @NonNull
    private Drawable cover;
    /**
     * Selection state.
     */
    @Setter
    private boolean selected;

    @Getter
    private boolean initialized;

    public final void init(@NonNull String url, @NonNull File output, @NonNull String format, boolean backup) {
        this.url = url;
        this.book = null;
        this.output = output;
        this.format = format;
        this.backup = backup;
        initialized = true;
    }

    public final void init(@NonNull CrawlerBook book, @NonNull File output, @NonNull String format, boolean backup) {
        this.url = null;
        this.book = book;
        this.output = output;
        this.format = format;
        this.backup = backup;
        initialized = true;
    }


    /**
     * Cleans up the task.
     */
    public abstract void cleanup();

    /**
     * Gets state of the task.
     *
     * @return the state
     */
    public abstract State getState();

    /**
     * Gets number of currently fetched text of chapters.
     *
     * @return the count, of {@literal -1} if unknown
     */
    public abstract int getProgress();

    /**
     * Gets the error causing this task failed.
     *
     * @return the error
     */
    public abstract Throwable getError();
}
