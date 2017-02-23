package pw.phylame.support;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class TimedAction {
    private final long mLimit;

    private long mLastTime = 0;

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
