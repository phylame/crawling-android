package pw.phylame.crawling;

import android.app.Application;

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

    public void cleanup() {

    }
}
