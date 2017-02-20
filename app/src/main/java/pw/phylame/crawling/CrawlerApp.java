package pw.phylame.crawling;

import android.app.Activity;
import android.app.Application;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.val;

public class CrawlerApp extends Application {
    private static CrawlerApp app;

    public static CrawlerApp sharedApp() {
        return app;
    }

    public CrawlerApp() {
        app = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private final Set<Activity> activities = new LinkedHashSet<>();

    public void manage(Activity activity) {
        activities.add(activity);
    }

    public void finish() {
        for (val activity : activities) {
            activity.finish();
        }
    }
}
