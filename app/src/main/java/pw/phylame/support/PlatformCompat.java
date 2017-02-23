package pw.phylame.support;

import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import lombok.val;
import pw.phylame.commons.io.IOUtils;
import pw.phylame.commons.value.Lazy;

public final class PlatformCompat {
    private PlatformCompat() {
    }

    private static final Lazy<Properties> sProp = new Lazy<>(() -> {
        val prop = new Properties();
        val in = new FileInputStream(new File(Environment.getRootDirectory(), "build.prop"));
        try {
            prop.load(in);
        } finally {
            IOUtils.closeQuietly(in);
        }
        return prop;
    });

    public static boolean isMIUI() {
        return sProp.get().containsKey("ro.miui.ui.version.name");
    }

    public static boolean isFlyme() {
        try {
            return Build.class.getMethod("hasSmartBar") != null;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    public static boolean isEMUI() {
        return sProp.get().containsKey("ro.build.version.emui");
    }
}
