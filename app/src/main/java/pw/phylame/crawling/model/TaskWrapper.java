package pw.phylame.crawling.model;

import android.util.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jem.Chapter;
import jem.crawler.CrawlerListener;
import jem.crawler.CrawlerListenerAdapter;
import jem.epm.EpmManager;
import jem.util.JemException;
import lombok.val;
import pw.phylame.commons.value.Lazy;
import pw.phylame.support.RxBus;

class TaskWrapper extends ITask implements Runnable {
    private static final String TAG = TaskWrapper.class.getSimpleName();
    private static final Lazy<ExecutorService> sExecutor = new Lazy<>(() -> Executors.newFixedThreadPool(48));

    State mState;
    int mPosition;
    Future<?> mFuture;
    private List<Future<?>> mFutures = new LinkedList<>();

    @Override
    public State getState() {
        return mState;
    }

    private void setState(State state) {
        setState(state, null);
    }

    private void setState(State state, Object data) {
        mState = state;
        RxBus.getDefault().post(TaskEvent
                .obtain()
                .type(TaskEvent.EVENT_LIFECYCLE)
                .arg1(mPosition)
                .obj(data));
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
        setState(ITask.State.Started);
    }

    void pause() {
        if (mState == ITask.State.Started) {
            Log.d(TAG, "already started: " + this);
            return;
        } else if (mState != ITask.State.Paused) {
            throw new IllegalStateException("Task is not paused: " + this);
        }
        setState(ITask.State.Paused);
    }

    private CrawlerListener mListener = new CrawlerListenerAdapter() {
        @Override
        public void textFetched(Chapter chapter, int total, int current) {
            RxBus.getDefault().post(TaskEvent
                    .obtain()
                    .type(TaskEvent.EVENT_PROGRESS)
                    .arg1(mPosition)
                    .arg2(current));
        }
    };

    @Override
    public void run() {
        setState(State.Started);
        val book = getBook();
        book.getContext().setListener(mListener);
        mFutures = book.initTexts(sExecutor.get(), false);
        try {
            book.awaitFetched();
        } catch (InterruptedException e) {
            setState(State.Cancelled);
            return;
        }
        try {
            EpmManager.writeBook(book, getOutput(), getFormat(), null);
            setState(State.Finished);
        } catch (IOException | JemException e) {
            setState(State.Failed, e);
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
