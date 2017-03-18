package pw.phylame.support;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import pw.phylame.commons.function.Provider;
import pw.phylame.commons.value.Lazy;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public final class Worker {
    private Worker() {
    }

    private static final Action1<Throwable> IgnoredAction = (e) -> {
    };

    private static final Lazy<ExecutorService> sPool = new Lazy<>(() -> Executors.newFixedThreadPool(4));

    public static void execute(@NonNull Runnable task) {
        sPool.get().submit(task);
    }

    public static <T> void execute(@NonNull Provider<T> provider, @NonNull Action1<T> success) {
        execute(provider, success, IgnoredAction);
    }

    public static <T> void execute(@NonNull Provider<T> provider,
                                   @NonNull Action1<? super T> success,
                                   @NonNull Action1<Throwable> error) {
        Observable.create(new ProviderOnSubscribe<>(provider))
                .subscribeOn(Schedulers.from(sPool.get()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success, error);
    }

    public static void cleanup() {
        if (sPool.isInitialized()) {
            sPool.get().shutdown();
        }
    }

    @RequiredArgsConstructor
    private static class ProviderOnSubscribe<T> implements Observable.OnSubscribe<T> {
        private final Provider<T> provider;

        @Override
        public void call(Subscriber<? super T> sub) {
            try {
                sub.onNext(provider.provide());
                sub.onCompleted();
            } catch (Exception e) {
                sub.onError(e);
            }
        }
    }
}
