package pw.phylame.crawling.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.graphics.Palette;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.jaeger.library.StatusBarUtil;

import jem.Attributes;
import jp.wasabeef.glide.transformations.BlurTransformation;
import lombok.val;
import pw.phylame.crawling.R;
import pw.phylame.crawling.model.ITask;
import pw.phylame.crawling.util.Jem;
import pw.phylame.support.DataHub;
import pw.phylame.support.StatusBarCompat;
import pw.phylame.support.Worker;

import static pw.phylame.support.Views.viewById;

public class DetailsActivity extends BaseActivity {
    public static final int TASK_KEY = 100;
    private ImageView mBanner;
    private TextView mBoard;
    private CollapsingToolbarLayout mCollapsingLayout;
    private int mIconColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        setupToolbar(R.id.toolbar, true);
        StatusBarUtil.setTranslucentForImageView(this, 0, mBanner);
        ((CollapsingToolbarLayout.LayoutParams) mToolbar.getLayoutParams())
                .setMargins(0, StatusBarCompat.getStatusHeight(this), 0, 0);

        mBanner = viewById(this, R.id.banner);
        mBoard = viewById(this, R.id.board);
        mCollapsingLayout = viewById(this, R.id.collapsing_layout);

        ((AppBarLayout) findViewById(R.id.appbar)).addOnOffsetChangedListener(((bar, offset) -> {
            if (offset == 0) {
                mBoard.setVisibility(View.VISIBLE);
            } else if (Math.abs(offset) == bar.getTotalScrollRange()) {
                mBoard.setVisibility(View.GONE);
            } else {
                mBoard.setAlpha(1 - Math.abs(offset) / (float) bar.getTotalScrollRange());
            }
        }));

        init();
    }

    private void init() {
        ITask task = DataHub.take(TASK_KEY);
        val book = task.getBook();
        if (book != null) {
            setTitle(Attributes.getTitle(book));
            val intro = Attributes.getIntro(book);
            if (intro != null) {
                mBoard.setText(intro.getText());
            }
            val cover = Attributes.getCover(book);
            if (cover != null) {
                Worker.execute(() -> Jem.cache(cover, getExternalCacheDir()), cache -> {
                    Glide.with(this)
                            .load(cache)
                            .crossFade()
                            .bitmapTransform(new BlurTransformation(this, 21, 4))
                            .listener(new RequestListener<Object, GlideDrawable>() {
                                @Override
                                public boolean onException(Exception e, Object model, Target<GlideDrawable> target, boolean isFirstResource) {
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(GlideDrawable resource, Object model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                    val bmp = ((GlideBitmapDrawable) resource).getBitmap();
                                    val palette = Palette.from(bmp).generate();
                                    int color = palette.getVibrantSwatch().getBodyTextColor();
                                    mToolbar.setTitleTextColor(color);
                                    mBoard.setTextColor(color);
                                    DrawableCompat.setTint(mToolbar.getNavigationIcon(), color);
                                    setStatusMode(palette.getDominantColor(Color.BLACK));
                                    return false;
                                }
                            }).into(mBanner);
                });
            }
        }
    }

    private void setStatusMode(int color) {
        if (ColorUtils.calculateLuminance(color) < 0.5) {
            StatusBarCompat.setStatusMode(getWindow(), false);
        } else {
            StatusBarCompat.setStatusMode(getWindow(), true);
        }
    }
}
