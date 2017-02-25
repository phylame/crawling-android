package pw.phylame.crawling;

import android.app.Activity;
import android.app.Application;

import java.util.LinkedHashSet;
import java.util.Set;

import pw.phylame.commons.function.Functionals;

public class CrawlerApp extends Application {
    private static CrawlerApp sApp;

    public static CrawlerApp sharedApp() {
        return sApp;
    }

    public CrawlerApp() {
        sApp = this;
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
        Functionals.foreach(activities.iterator(), Activity::finish);
    }
}
