package pw.phylame.support;

import lombok.val;

public class TimedAction {
    private long mLastTime = 0;
    private final long mLimit;

    public TimedAction(long millis) {
        this.mLimit = millis;
    }

    public boolean isEnable() {
        val now = System.currentTimeMillis();
        if (now - mLastTime < mLimit) {
            mLastTime = 0;
            return true;
        } else {
            mLastTime = now;
            return false;
        }
    }
}
