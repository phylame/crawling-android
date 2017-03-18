package pw.phylame.crawling.model;

import android.util.Log;

import java.io.File;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import jem.Attributes;
import jem.Chapter;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import jem.crawler.CrawlerManager;
import jem.crawler.TextFetchListener;
import jem.epm.EpmManager;
import lombok.val;
import pw.phylame.commons.util.Validate;

class TaskWrapper extends ITask implements Runnable {
    private static final String TAG = TaskWrapper.class.getSimpleName();

    int mPosition;
    private State mState;
    private int mLastProgress = -1;
    private volatile int mProgress = 0;
    private volatile Throwable mError;

    Future<?> mFuture;
    ExecutorService mExecutor; // reused executor
    private final WeakReference<TaskBinder> mBinder;

    private final TextFetchListener mListener = new TextFetchListener() {
        @Override
        public void textFetched(Chapter chapter, int total, int current) {
            mProgress = (int) Math.round(current / (double) total * 100);
            if (mProgress != mLastProgress) { // prevent posting same progress
                mLastProgress = mProgress;
                ITask.postMessage(ITask.EVENT_PROGRESS, mPosition, mProgress);
            }
        }
    };

    TaskWrapper(TaskBinder binder) {
        mBinder = new WeakReference<>(binder);
    }

    @Override
    public final State getState() {
        return mState;
    }

    final void setState(State state) {
        mState = state;
        ITask.postMessage(ITask.EVENT_STATE, mPosition, 0);
    }

    @Override
    public final int getProgress() {
        return getBook() != null ? mProgress : 0;
    }

    @Override
    public final Throwable getError() {
        return mError;
    }

    private void setError(Throwable error) {
        mError = error;
        setState(State.Failed);
    }

    final boolean cancel() {
        getBook().cancelFetch();
        return mFuture.isDone() || mFuture.cancel(true);
    }

    final void start() {
        if (mState == State.Started) {
            throw new IllegalStateException("Task is already started: " + this);
        } else if (mState != State.Paused) {
            throw new IllegalStateException("Task is not paused: " + this);
        }
        setState(State.Submitted);
        mBinder.get().scheduleTask(this); // wrapper already submitted to task manager
    }

    final void pause() {
        if (mState == State.Paused) {
            throw new IllegalStateException("Task is already paused: " + this);
        } else if (mState != State.Started) {
            throw new IllegalStateException("Task is not started or submitted: " + this);
        }
        if (cancel()) {
            setState(ITask.State.Paused);
        } else {
            Log.e(TAG, "cannot cancel task: " + this);
        }
    }

    @Override
    public final void run() {
        CrawlerBook book = getBook();
        if (book == null) { // no prepared book, task from URL
            try {
                book = CrawlerManager.fetchBook(getUrl(), new CrawlerConfig());
                setBook(book);
                ITask.postMessage(ITask.EVENT_FETCHED, mPosition, 0);
            } catch (Exception e) {
                setError(e);
                return;
            }
        }
        setState(State.Started);
        book.getContext().setListener(mListener);
        book.fetchTexts(mExecutor, mProgress);
        try {
            val name = Attributes.getTitle(book) + '.' + getFormat();
            File file = new File(getOutput(), name);
            EpmManager.writeBook(book, file, getFormat(), null);
            if (isBackup() && !getFormat().equals(EpmManager.PMAB)) {
                file = new File(getOutput(), name + '.' + EpmManager.PMAB);
                EpmManager.writeBook(book, file, EpmManager.PMAB, null);
            }
            setState(State.Finished);
        } catch (CancellationException | InterruptedIOException e) {
            Log.d(TAG, "cancelled or interrupted", e);
//            book.cancelFetch();
        } catch (Exception e) {
            book.cancelFetch();
            setError(e);
        }
    }

    @Override
    public final void cleanup() {
        Validate.check(mFuture.isDone(), "task is not done");
        getBook().cleanup();
    }
}
