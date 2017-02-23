package pw.phylame.support;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import lombok.val;

/**
 * Utilities for activity.
 */
public final class Activities {
    private Activities() {
    }

    public static void startActivity(Activity activity, Class<? extends Activity> target) {
        val intent = new Intent(activity, target);
        activity.startActivity(intent);
    }
}
