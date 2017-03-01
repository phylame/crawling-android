package pw.phylame.crawling.model;

import android.graphics.drawable.Drawable;

import java.io.File;

import jem.Attributes;
import jem.crawler.CrawlerBook;
import jem.crawler.CrawlerConfig;
import lombok.NonNull;

public abstract class ITask {
    public enum State {
        Submitted, Started, Paused, Finished, Failed, Cancelled, Deleted
    }

    private String mURL;
    private File mOutput;
    private String mFormat;
    private boolean mBackup;
    private CrawlerBook mBook;

    public Drawable cover;
    public boolean selected;

    public abstract State getState();

    public abstract int getTotal();

    public abstract int getProgress();

    public String getURL() {
        return mURL;
    }

    public void setURL(String url) {
        this.mURL = url;
    }

    public File getOutput() {
        return mOutput;
    }

    public void setOutput(@NonNull File output) {
        this.mOutput = output;
    }

    public String getFormat() {
        return mFormat;
    }

    public void setFormat(@NonNull String format) {
        this.mFormat = format;
    }

    public boolean isBackup() {
        return mBackup;
    }

    public void setBackup(boolean backup) {
        this.mBackup = backup;
    }

    public CrawlerBook getBook() {
        return mBook;
    }

    public void setBook(@NonNull CrawlerBook book) {
        this.mBook = book;
    }
}
