package pw.phylame.support;

import android.app.Activity;
import android.content.Intent;

import lombok.val;

/**
 * Utilities for activity.
 */
public final class Activities {
    private Activities() {
    }

    public static void startActivity(Activity context, Class<? extends Activity> target) {
        val intent = new Intent(context, target);
        context.startActivity(intent);
    }

    public static void startActivityForResult(Activity context, Class<? extends Activity> target, int requestCode) {
        val intent = new Intent(context, target);
        context.startActivityForResult(intent, requestCode);
    }
}
