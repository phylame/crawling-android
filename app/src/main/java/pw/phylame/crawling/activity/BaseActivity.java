package pw.phylame.crawling.activity;

import android.support.annotation.IdRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.jaeger.library.StatusBarUtil;
import com.tbruyelle.rxpermissions.RxPermissions;

import lombok.NonNull;
import lombok.val;
import pw.phylame.commons.function.Consumer;
import pw.phylame.commons.value.Lazy;
import pw.phylame.crawling.R;
import pw.phylame.support.StatusBarCompat;

import static pw.phylame.support.Views.viewById;

public abstract class BaseActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    protected final Lazy<RxPermissions> mPermissions = new Lazy<>(() -> new RxPermissions(this));

    protected final void requestPermission(@NonNull String permission, @NonNull Consumer<Boolean> action) {
        mPermissions.get()
                .request(permission)
                .subscribe(action::consume);
    }

    protected final void requestPermissions(@NonNull String[] permissions, @NonNull Consumer<Boolean> action) {
        mPermissions.get()
                .request(permissions)
                .subscribe(action::consume);
    }

    protected Toolbar mToolbar;

    protected final void setupToolbar(@IdRes int id) {
        setupToolbar(id, false);
    }

    protected final void setupToolbar(@IdRes int id, boolean homeAsUp) {
        mToolbar = viewById(this, id);
        setSupportActionBar(mToolbar);
        val actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(homeAsUp);
        if (homeAsUp) {
            mToolbar.setNavigationIcon(R.mipmap.ic_back);
        }
    }

    private TextView mTitleView;

    protected final void setupCenteredToolbar(@IdRes int id) {
        setupCenteredToolbar(id, false);
    }

    protected final void setupCenteredToolbar(@IdRes int id, boolean homeAsUp) {
        mToolbar = viewById(this, id);
        setSupportActionBar(mToolbar);
        mTitleView = viewById(mToolbar, R.id.title);
        val actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(homeAsUp);
        if (homeAsUp) {
            mToolbar.setNavigationIcon(R.mipmap.ic_back);
        }
    }

    protected final void setupColoredStatus() {
        val color = ContextCompat.getColor(this, R.color.colorPrimary);
        if (ColorUtils.calculateLuminance(color) < 0.5 || StatusBarCompat.setStatusMode(getWindow(), true)) {
            StatusBarUtil.setColor(this, color, 0);
        } else {
            StatusBarUtil.setColor(this, ContextCompat.getColor(this, R.color.fallbackStatusBarColor), 0);
        }
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        } else {
            super.onTitleChanged(title, color);
        }
    }
}
