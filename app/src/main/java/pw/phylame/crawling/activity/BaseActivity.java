package pw.phylame.crawling.activity;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.LayoutRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AppCompatActivity;

import com.tbruyelle.rxpermissions.RxPermissions;

import lombok.NonNull;
import lombok.val;
import pw.phylame.commons.function.Consumer;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.support.StatusBarCompat;

public abstract class BaseActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    protected final Lazy<RxPermissions> mPermissions = new Lazy<>(() -> {
        return new RxPermissions(this);
    });

    protected final void requestPermission(@NonNull String permission, @NonNull Consumer<Boolean> action) {
        mPermissions
                .get()
                .request(permission)
                .subscribe(action::consume);
    }

    protected final void requestPermissions(@NonNull String[] permissions, @NonNull Consumer<Boolean> action) {
        mPermissions
                .get()
                .request(permissions)
                .subscribe(action::consume);
    }

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
