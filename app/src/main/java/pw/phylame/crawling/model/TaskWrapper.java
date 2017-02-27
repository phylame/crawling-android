package pw.phylame.crawling.model;

import android.util.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jem.Chapter;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerListener;
import jem.crawler.CrawlerListenerAdapter;
import jem.crawler.CrawlerText;
import jem.epm.EpmManager;
import jem.epm.EpmOutParam;
import jem.util.JemException;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.val;
import pw.phylame.commons.value.Lazy;
import pw.phylame.support.RxBus;

class TaskWrapper extends ITask implements Runnable {
    private static final String TAG = TaskWrapper.class.getSimpleName();
    static final Lazy<ExecutorService> sExecutor = new Lazy<>(() -> Executors.newFixedThreadPool(48));

    State mState;
    int mPosition;
    Future<?> mFuture;
    private List<Future<?>> mFutures = new LinkedList<>();

    @Override
    public State getState() {
        return mState;
    }

    @Override
    public int getTotal() {
        return getBook().getTotalChapters();
    }

    boolean cancel() {
        if (mFuture.isDone() || mFuture.isCancelled()) {
            return true;
        }
        for (val future : mFutures) {
            if (!future.isDone() && !future.isCancelled()) {
                future.cancel(true);
            }
        }
        mFutures.clear();
        return mFuture.cancel(true);
    }

    void start() {
        if (mState == ITask.State.Paused) {
            Log.d(TAG, "already paused: " + this);
            return;
        } else if (mState != ITask.State.Started) {
            throw new IllegalStateException("Task is not started: " + this);
        }
        mState = ITask.State.Started;
        RxBus.getDefault().post(TaskEvent.obtain()
                .type(TaskEvent.EVENT_LIFECYCLE)
                .arg1(mPosition)
                .obj(this));
    }

    void pause() {
        if (mState == ITask.State.Started) {
            Log.d(TAG, "already started: " + this);
            return;
        } else if (mState != ITask.State.Paused) {
            throw new IllegalStateException("Task is not paused: " + this);
        }
        mState = ITask.State.Paused;
        RxBus.getDefault().post(TaskEvent.obtain()
                .type(TaskEvent.EVENT_LIFECYCLE)
                .arg1(mPosition)
                .obj(this));
    }

    private CrawlerListener mListener = new CrawlerListenerAdapter() {
        @Override
        public void textFetching(Chapter chapter, int total, int current) {
            RxBus.getDefault().post(TaskEvent.obtain()
                    .type(TaskEvent.EVENT_PROGRESS)
                    .arg1(mPosition)
                    .arg2(current)
                    .obj(this));
        }
    };

    @Override
    public void run() {
        mState = State.Started;
        RxBus.getDefault().post(TaskEvent.obtain()
                .type(TaskEvent.EVENT_LIFECYCLE)
                .arg1(mPosition)
                .obj(this));

        val book = getBook();
        book.getContext().setListener(mListener);
        mFutures = book.initTexts(sExecutor.get(), false);
        try {
            EpmManager.writeBook(getBook(), getOutput(), getFormat(), null);
            mState = State.Finished;
            RxBus.getDefault().post(TaskEvent.obtain()
                    .type(TaskEvent.EVENT_LIFECYCLE)
                    .arg1(mPosition)
                    .obj(this));
        } catch (IOException | JemException e) {
            mState = State.Failed;
            RxBus.getDefault().post(TaskEvent.obtain()
                    .type(TaskEvent.EVENT_ERROR)
                    .arg1(mPosition)
                    .obj(e));
        }
    }

    private void onCancelled() {
        cleanup();
        mState = ITask.State.Started;
        RxBus.getDefault().post(TaskEvent.obtain()
                .type(TaskEvent.EVENT_CANCELLED)
                .arg1(mPosition)
                .obj(this));
    }

    private void cleanup() {
    }
}
