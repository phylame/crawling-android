package pw.phylame.crawling.model;

import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jem.Chapter;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import jem.crawler.CrawlerListener;
import jem.crawler.CrawlerListenerAdapter;
import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;
import jem.epm.util.ParserException;
import jem.util.JemException;
import lombok.val;
import pw.phylame.commons.util.Validate;
import pw.phylame.commons.value.Lazy;
import pw.phylame.support.RxBus;

class TaskWrapper extends ITask implements Runnable {
    private static final String TAG = TaskWrapper.class.getSimpleName();
    private static final Lazy<ExecutorService> sExecutor = new Lazy<>(() -> Executors.newFixedThreadPool(48));

    State mState;
    int mPosition;
    Future<?> mFuture;
    private volatile int mProgress = 0;
    WeakReference<TaskBinder> mBinder;
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
        val book = getBook();
        return book != null ? book.getTotalChapters() : -1;
    }

    @Override
    public int getProgress() {
        return getBook() != null ? mProgress : -1;
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
        System.out.println("TaskWrapper.start");
        if (mState == State.Started) {
            throw new IllegalStateException("Task is already started: " + this);
        } else if (mState != State.Paused) {
            throw new IllegalStateException("Task is not paused: " + this);
        }
        Validate.requireNotNull(mBinder, "mBinder is null");
        mBinder.get().submitTask0(this);
        setState(State.Started);
    }

    void pause() {
        System.out.println("TaskWrapper.pause");
        if (mState == State.Paused) {
            throw new IllegalStateException("Task is already paused: " + this);
        } else if (mState != State.Started && mState != State.Submitted) {
            throw new IllegalStateException("Task is not started or submitted: " + this);
        }
        if (cancel()) {
            setState(ITask.State.Paused);
        }
    }

    private CrawlerListener mListener = new CrawlerListenerAdapter() {
        @Override
        public void textFetched(Chapter chapter, int total, int current) {
            mProgress = current;
            RxBus.getDefault().post(TaskEvent
                    .obtain()
                    .type(TaskEvent.EVENT_PROGRESS)
                    .arg1(mPosition)
                    .obj(chapter));
        }
    };

    @Override
    public void run() {
        CrawlerBook book = getBook();
        if (book == null) {
            try {
                book = CrawlerManager.fetchBook(getURL(), new CrawlerConfig());
                setBook(book);
                RxBus.getDefault().post(TaskEvent
                        .obtain()
                        .type(TaskEvent.EVENT_FETCHED)
                        .arg1(mPosition));
            } catch (IOException | ParserException e) {
                e.printStackTrace();
                setState(State.Failed, e);
                return;
            }
        } else {
            try {
                book.getContext().getCrawler().get().fetchAttributes();
            } catch (IOException e) {
                e.printStackTrace();
                setState(State.Failed, e);
                return;
            }
        }
        setState(State.Started);
        book.getContext().setListener(mListener);
        mFutures = book.initTexts(sExecutor.get(), mProgress);
        try {
            book.awaitFetched();
        } catch (InterruptedException e) {
            Log.d(TAG, "interrupt waiting for fetching");
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

    public void cleanup() {
        getBook().cleanup();
    }
}
