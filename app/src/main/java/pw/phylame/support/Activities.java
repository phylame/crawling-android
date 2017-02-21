package pw.phylame.support;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

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
}
