package pw.phylame.crawling.model;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.val;

public class TaskExecutor extends ThreadPoolExecutor {
    private final Queue<ExecutorService> mTextExecutors = new LinkedList<>();

    public TaskExecutor(int nTasks, int nTexts) {
        super(nTasks, nTasks, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        for (int i = 0; i < nTasks; ++i) {
            mTextExecutors.offer(Executors.newFixedThreadPool(nTexts));
        }
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new InternalFutureTask<>(runnable, value);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        val wrapper = (TaskWrapper) ((InternalFutureTask) r).runnable;
        wrapper.mExecutor = mTextExecutors.poll();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        val wrapper = (TaskWrapper) ((InternalFutureTask) r).runnable;
        mTextExecutors.offer(wrapper.mExecutor);
        wrapper.mExecutor = null;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        for (val pool : mTextExecutors) {
            pool.shutdown();
        }
    }

    private static class InternalFutureTask<V> extends FutureTask<V> {
        private final Runnable runnable;

        InternalFutureTask(Runnable runnable, V result) {
            super(runnable, result);
            this.runnable = runnable;
        }
    }
}
