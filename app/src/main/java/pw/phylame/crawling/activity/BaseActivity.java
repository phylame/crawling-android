package pw.phylame.crawling.activity;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.LayoutRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AppCompatActivity;

import lombok.val;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.support.StatusBarCompat;

public abstract class BaseActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        CrawlerApp.sharedApp().manage(this);
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        val color = ContextCompat.getColor(this, R.color.colorPrimary);
        if (ColorUtils.calculateLuminance(color) < 0.5 || StatusBarCompat.setStatusMode(getWindow(), true)) {
            StatusBarCompat.setStatusColor(this, color);
        }
        super.setContentView(layoutResID);
    }
}
