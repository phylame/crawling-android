package pw.phylame.crawling.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import lombok.val;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.R;
import pw.phylame.crawling.model.TaskBinder;
import pw.phylame.crawling.model.TaskExecutor;
import pw.phylame.crawling.util.Settings;

public class TaskService extends Service {
    private TaskBinder mBinder;
    private final Lazy<TaskExecutor> mExecutor = new Lazy<>(() -> {
        val prefs = Settings.generalSettings(this);
        int tasks = prefs.getInt("task.activeThreads", -1);
        if (tasks < 0) {
            tasks = Math.max(getResources().getInteger(R.integer.default_task_count), Runtime.getRuntime().availableProcessors());
        }
        int texts = prefs.getInt("task.textThreads", -1);
        if (texts < 0) {
            texts = getResources().getInteger(R.integer.default_text_count);
        }
        return new TaskExecutor(tasks, texts);
    });

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new TaskBinder(mExecutor.get());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinder.cleanup();
        if (mExecutor.isInitialized()) {
            mExecutor.get().shutdown();
        }
    }
}
