package pw.phylame.crawling.downloading;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FetchService extends Service {
    private ExecutorService mExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private List<Task> mTasks = Collections.synchronizedList(new ArrayList<Task>());

    private FetchBinder mBinder = new FetchBinder();

    private OnTaskProgressListener mOnTaskProgressListener;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    Random random = new Random();

    private class FetchTask implements Runnable {
        private final Task task;
        private final int position;

        FetchTask(Task task, int position) {
            this.task = task;
            this.position = position;
        }

        @Override
        public void run() {
            task.state = Task.State.Started;
            for (int i = 0; i < task.total; i++) {
                try {
                    Thread.sleep(random.nextInt(10));
                    ++task.progress;
                    if (mOnTaskProgressListener != null) {
                        task.changeSource = Task.ChangeSource.Progress;
                        mOnTaskProgressListener.onChange(task, position);
                    }
                } catch (InterruptedException ignored) {
                    task.state = Task.State.Failed;
                }
            }
//            mTasks.remove(position);
            task.state = Task.State.Finished;
//            if (mOnTaskProgressListener != null) {
//                mOnTaskProgressListener.onDone(task, position);
//            }
        }
    }

    private class FetchBinder extends Binder implements ITaskManager {
        @Override
        public int getCount() {
            return mTasks.size();
        }

        @Override
        public List<Task> getTasks() {
            return mTasks;
        }

        @Override
        public Task getTask(int index) {
            return mTasks.get(index);
        }

        @Override
        public boolean newTask(Task task) {
            if (mTasks.contains(task)) {
                return false;
            }
            mTasks.add(task);
            mExecutor.submit(new FetchTask(task, mTasks.size() - 1));
            return true;
        }

        @Override
        public void startTask(Task task, boolean start) {

        }

        @Override
        public void startTasks(Collection<Task> tasks, boolean start) {

        }

        @Override
        public void deleteTask(Task task) {

        }

        @Override
        public boolean deleteTasks(Collection<Task> tasks) {
            if (tasks.isEmpty()) {
                return false;
            }
            mTasks.removeAll(tasks);
            return true;
        }

        @Override
        public void setOnProgressChangeListener(OnTaskProgressListener l) {
            mOnTaskProgressListener = l;
        }
    }
}
