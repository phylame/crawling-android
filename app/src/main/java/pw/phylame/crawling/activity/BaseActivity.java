package pw.phylame.crawling.activity;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.tbruyelle.rxpermissions.RxPermissions;

import lombok.NonNull;
import lombok.val;
import pw.phylame.commons.function.Consumer;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.R;
import pw.phylame.support.StatusBarCompat;

import static pw.phylame.support.Views.viewById;

public abstract class BaseActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    protected final Lazy<RxPermissions> mPermissions = new Lazy<>(() -> new RxPermissions(this));

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

    private TextView mTitleView;

    protected final void setupCenteredToolbar(@IdRes int id) {
        Toolbar toolbar = viewById(this, id);
        setSupportActionBar(toolbar);
        mTitleView = viewById(toolbar, R.id.title);
        val actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(false);
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        } else {
            super.onTitleChanged(title, color);
        }
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
