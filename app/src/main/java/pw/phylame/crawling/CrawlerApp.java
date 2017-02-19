package pw.phylame.crawling;

import android.app.Application;
import android.content.res.Configuration;

import java.util.Locale;

import lombok.val;

public class CrawlerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Locale.setDefault(Locale.US);
        val config = new Configuration();
        config.locale = Locale.ENGLISH;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }
}
