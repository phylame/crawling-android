package pw.phylame.crawling.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
    public static SharedPreferences generalSettings(Context context) {
        return context.getSharedPreferences("general", Context.MODE_PRIVATE);
    }
}
