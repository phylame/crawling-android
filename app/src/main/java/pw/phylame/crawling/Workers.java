package pw.phylame.crawling;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import pw.phylame.commons.function.Provider;
import pw.phylame.commons.value.Lazy;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public final class Workers {
    private Workers() {
    }

    private static final Lazy<ExecutorService> sExecutor = new Lazy<>(() -> Executors.newFixedThreadPool(4));

    public static void execute(@NonNull Runnable task) {
        sExecutor.get().submit(task);
    }

    public static <T> void execute(@NonNull Provider<T> provider, @NonNull Action1<T> success) {
        execute(provider, success, null);
    }

    public static <T> void execute(@NonNull Provider<T> provider, @NonNull Action1<T> success, Action1<Throwable> error) {
        Observable
                .create(new ProviderOnSubscribe<>(provider))
                .subscribeOn(Schedulers.from(sExecutor.get()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success, error != null ? error : IgnoredAction.IGNORED_ACTION);
    }

    public static void cleanup() {
        if (sExecutor.isInitialized()) {
            sExecutor.get().shutdown();
        }
    }

    @RequiredArgsConstructor
    private static class ProviderOnSubscribe<T> implements Observable.OnSubscribe<T> {
        private final Provider<T> provider;

        @Override
        public void call(Subscriber<? super T> subscriber) {
            try {
                subscriber.onNext(provider.provide());
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }
    }

    private static class IgnoredAction implements Action1<Throwable> {
        private static final IgnoredAction IGNORED_ACTION = new IgnoredAction();

        @Override
        public void call(Throwable t) {

        }
    }
}
