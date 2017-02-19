package pw.phylame.support;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.widget.DrawerLayout;
import android.view.ViewGroup;

import lombok.val;

/**
 * Utilities for activity.
 */
public final class Activities {
    private Activities() {
    }

    public static void startActivity(Context context, Class<? extends Activity> target) {
        Intent intent = new Intent(context, target);
        context.startActivity(intent);
    }

    public static void setFitsSystemWindows(Activity activity) {
        val view = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        if (view instanceof DrawerLayout) {
            ((DrawerLayout) view).getChildAt(0).setFitsSystemWindows(true);
        } else {
            view.setFitsSystemWindows(true);
        }
    }
}
