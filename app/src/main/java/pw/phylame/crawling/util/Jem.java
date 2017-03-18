package pw.phylame.crawling.util;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;
import jem.util.flob.Flob;
import lombok.NonNull;
import lombok.val;
import pw.phylame.commons.io.IOUtils;

public final class Jem {
    private static final String TAG = Jem.class.getSimpleName();
    private static volatile boolean sLoaded = false;

    public static void init() {
        if (sLoaded) {
            return;
        }
        CrawlerManager.loadCrawlers();
        EpmManager.loadImplementors();
        sLoaded = true;
    }

    public static File cache(@NonNull Flob flob, @NonNull File dir) {
        File file = null;
        OutputStream out = null;
        try {
            val name = Integer.toHexString(flob.toString().hashCode());
            file = new File(dir, name);
            if (file.exists()) {
                return file;
            }
            flob.writeTo(out = new FileOutputStream(file));
        } catch (IOException e) {
            if (out != null) {
                IOUtils.closeQuietly(out);
                out = null;
                if (!file.delete()) {
                    Log.e(TAG, "cannot delete cache file: " + file);
                }
            }
            return null;
        } finally {
            IOUtils.closeQuietly(out);
        }
        return file;
    }
}
