package pw.phylame.support;

import android.app.Activity;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import lombok.val;
import pw.phylame.commons.util.Reflections;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public final class StatusBarCompat {
    private static final String TAG = StatusBarCompat.class.getSimpleName();

    public static int getStatusHeight(Activity activity) {
        val resources = activity.getResources();
        return resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"));
    }

    public static boolean setStatusColor(Activity activity, @ColorInt int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(color);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val window = activity.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            ((ViewGroup) window.getDecorView())
                    .addView(Views.coloredView(activity, color, MATCH_PARENT, getStatusHeight(activity)));
        } else {
            return false;
        }
        return true;
    }

    public static boolean setStatusMode(Window window, boolean darker) {
        if (PlatformCompat.isMIUI()) {
            return setMiuiStatusMode(window, darker);
        } else if (PlatformCompat.isFlyme()) {
            return setFlymeStatusMode(window, darker);
        } else {
            return setAndroidMStatusMode(window, darker);
        }
    }

    public static boolean setMiuiStatusMode(Window window, boolean darker) {
        try {
            val layoutClass = Class.forName("android.view.MiuiWindowManager$LayoutParams");
            val flag = Reflections.getFieldValue(layoutClass, "EXTRA_FLAG_STATUS_BAR_DARK_MODE");
            Reflections.Invocation.builder()
                    .name("setExtraFlags")
                    .target(window)
                    .types(new Class[]{int.class, int.class})
                    .arguments(new Object[]{darker ? flag : 0, flag})
                    .build()
                    .invoke();
            return true;
        } catch (Exception e) {
            Log.d(TAG, "cannot set status mode for miui", e);
            return false;
        }
    }

    public static boolean setFlymeStatusMode(Window window, boolean darker) {
        val attrs = window.getAttributes();
        try {
            val flag = (Integer) Reflections.getFieldValue(WindowManager.LayoutParams.class, "MEIZU_FLAG_DARK_STATUS_BAR_ICON");
            val field = WindowManager.LayoutParams.class.getDeclaredField("meizuFlags");
            field.setAccessible(true);
            int flags = field.getInt(attrs);
            if (darker) {
                flags |= flag;
            } else {
                flags &= ~flag;
            }
            field.setInt(attrs, flags);
            window.setAttributes(attrs);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "cannot set status mode for flyme", e);
            return false;
        }
    }

    public static boolean setAndroidMStatusMode(Window window, boolean darker) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (darker) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
            return true;
        }
        return false;
    }
}
