package pw.phylame.crawling.model;

import android.util.SparseArray;

import lombok.val;

public final class DataHub {
    private static final SparseArray<Object> sData = new SparseArray<>();

    public static void put(int key, Object o) {
        sData.put(key, o);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(int key) {
        return (T) sData.get(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T take(int key) {
        val obj = (T) sData.get(key);
        sData.remove(key);
        return obj;
    }

    public static void remove(int key) {
        sData.remove(key);
    }

    public static void clear() {
        sData.clear();
    }
}
