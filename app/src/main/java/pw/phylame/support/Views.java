package pw.phylame.support;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.support.annotation.ColorInt;
import android.support.annotation.IdRes;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import lombok.NonNull;
import lombok.val;

/**
 * Utilities for view.
 */
public final class Views {
    private Views() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends View> T viewById(@NonNull View parent, @IdRes int id) {
        return (T) parent.findViewById(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends View> T viewById(@NonNull Activity activity, @IdRes int id) {
        return (T) activity.findViewById(id);
    }

    public static void hideIME(Context context, View view) {
        val im = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void showIME(Context context, View view) {
        val im = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        im.showSoftInput(view, 0);
    }

    public static void animateVisibility(@NonNull View view, boolean shown) {
        val animTime = view.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
        view.setVisibility(shown ? View.VISIBLE : View.GONE);
        view.animate()
                .setDuration(animTime)
                .alpha(shown ? 1F : 0F)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(shown ? View.VISIBLE : View.GONE);
                    }
                });
    }

    /**
     * Creates a blank view with specified background color.
     *
     * @param context the context
     * @param color   the background color
     * @param width   layout width
     * @param height  layout height
     * @return the view with background color
     */
    public static View coloredView(Context context, @ColorInt int color, int width, int height) {
        val view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        view.setBackgroundColor(color);
        return view;
    }
}
