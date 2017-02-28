package pw.phylame.crawling.util;

import jem.crawler.CrawlerManager;
import jem.epm.EpmManager;

public final class JemUtils {
    private static volatile boolean sLoaded = false;

    public static void init() {
        if (sLoaded) {
            return;
        }
        CrawlerManager.loadCrawlers();
        EpmManager.loadImplementors();
        sLoaded = true;
    }
}
