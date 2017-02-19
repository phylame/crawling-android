package pw.phylame.support;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import lombok.val;

public class DividerItemDecoration extends RecyclerView.ItemDecoration {
    private static final int[] ATTRS = new int[]{android.R.attr.listDivider};

    public static final int HORIZONTAL = LinearLayoutManager.HORIZONTAL;
    public static final int VERTICAL = LinearLayoutManager.VERTICAL;

    private Drawable mDivider;

    private int mOrientation;
    private boolean mIsShowLastDivider = false;

    public DividerItemDecoration(Context context, int orientation) {
        val attrs = context.obtainStyledAttributes(ATTRS);
        mDivider = attrs.getDrawable(0);
        attrs.recycle();
        setOrientation(orientation);
    }

    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("Invalid orientation");
        }
        mOrientation = orientation;
    }

    public void setIsShowLastDivider(boolean shown) {
        mIsShowLastDivider = shown;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            drawVertical(c, parent);
        } else {
            drawHorizontal(c, parent);
        }
    }

    private void drawVertical(Canvas c, RecyclerView parent) {
        val left = parent.getPaddingLeft();
        val right = parent.getWidth() - parent.getPaddingRight();

        val end = mIsShowLastDivider ? parent.getChildCount() : parent.getChildCount() - 1;
        for (int i = 0; i < end; ++i) {
            val child = parent.getChildAt(i);
            val params = (RecyclerView.LayoutParams) child.getLayoutParams();
            val top = child.getBottom() + params.bottomMargin;
            val bottom = top + mDivider.getIntrinsicHeight();
            mDivider.setBounds(left, top + child.getPaddingTop(), right, bottom - child.getPaddingBottom());
            mDivider.draw(c);
        }
    }

    private void drawHorizontal(Canvas c, RecyclerView parent) {
        val top = parent.getPaddingTop();
        val bottom = parent.getHeight() - parent.getPaddingBottom();

        val end = mIsShowLastDivider ? parent.getChildCount() : parent.getChildCount() - 1;
        for (int i = 0; i < end; ++i) {
            val child = parent.getChildAt(i);
            val params = (RecyclerView.LayoutParams) child.getLayoutParams();
            val left = child.getRight() + params.rightMargin;
            val right = left + mDivider.getIntrinsicHeight();
            mDivider.setBounds(left + child.getPaddingLeft(), top, right - child.getPaddingRight(), bottom);
            mDivider.draw(c);
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            outRect.set(0, 0, 0, mDivider.getIntrinsicHeight());
        } else {
            outRect.set(0, 0, mDivider.getIntrinsicWidth(), 0);
        }
    }
}
