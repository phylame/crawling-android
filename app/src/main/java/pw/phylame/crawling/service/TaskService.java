package pw.phylame.crawling.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.val;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.R;
import pw.phylame.crawling.model.TaskBinder;

/**
 * Created by Mnelx on 2017-2-26.
 */

public class TaskService extends Service {
    private TaskBinder mBinder;
    private final Lazy<ExecutorService> mExecutor = new Lazy<ExecutorService>(() -> {
        val count = Math.max(getResources().getInteger(R.integer.init_task_limit), Runtime.getRuntime().availableProcessors() * 2);
        return Executors.newFixedThreadPool(count);
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
        if (mExecutor.isInitialized()) {
            mExecutor.get().shutdown();
        }
    }
}
