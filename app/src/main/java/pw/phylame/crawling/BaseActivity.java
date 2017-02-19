package pw.phylame.crawling;

import android.support.annotation.LayoutRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import pw.phylame.support.StatusBarCompat;

public abstract class BaseActivity extends AppCompatActivity {
    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        if (StatusBarCompat.setStatusMode(getWindow(), true)) {
            StatusBarCompat.setStatusColor(this, ContextCompat.getColor(this, R.color.colorPrimary));
        }
        super.setContentView(layoutResID);
    }
}
