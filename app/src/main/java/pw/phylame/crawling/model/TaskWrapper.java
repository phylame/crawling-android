package pw.phylame.crawling.model;

import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jem.Chapter;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerText;
import lombok.val;
import pw.phylame.commons.value.Lazy;
import pw.phylame.support.RxBus;

class TaskWrapper implements ITask, Runnable {
    private static final String TAG = TaskWrapper.class.getSimpleName();
    static final Lazy<ExecutorService> sExecutor = new Lazy<>(() -> Executors.newFixedThreadPool(48));

    State mState;
    int mPosition;
    CrawlerBook mBook;
    Future<?> mFuture;
    private List<Future<?>> mFutures = new LinkedList<>();

    @Override
    public State getState() {
        return mState;
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

    @Override
    public void run() {
        mState = ITask.State.Started;
        RxBus.getDefault().post(TaskEvent.obtain()
                .type(TaskEvent.EVENT_LIFECYCLE)
                .arg1(mPosition)
                .obj(this));

        for (val sub : mBook) {
            if (Thread.interrupted()) {
                onCancelled();
                return;
            }
            fetchTexts(sub);
        }
    }

    private void fetchTexts(Chapter chapter) {
        if (Thread.interrupted()) {
            onCancelled();
            return;
        }
        val text = chapter.getText();
        if (text instanceof CrawlerText) {
            val ct = (CrawlerText) text;
            if (!ct.isFetched() && !ct.isSubmitted()) {
                mFutures.add(sExecutor.get().submit(ct));
            }
        }
        for (val sub : chapter) {
            if (Thread.interrupted()) {
                onCancelled();
                return;
            }
            fetchTexts(sub);
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
