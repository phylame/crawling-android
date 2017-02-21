package pw.phylame.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.val;
import pw.phylame.commons.value.Lazy;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * An event bus implemented by RxJava.
 */
public final class RxBus {
    private static final Lazy<RxBus> sDefault = new Lazy<>(RxBus::new);

    private final Subject<Object, Object> mBus;
    private final Map<Class<?>, Object> mStickyEventMap;

    private RxBus() {
        mBus = new SerializedSubject<>(PublishSubject.create());
        mStickyEventMap = new ConcurrentHashMap<>();
    }

    /**
     * Gets the instance of the bus.
     *
     * @return the bus
     */
    public static RxBus getDefault() {
        return sDefault.get();
    }

    /**
     * Tests if some observer subscribed to the bus.
     *
     * @return {@literal true} if has observers
     */
    public final boolean hasObservers() {
        return mBus.hasObservers();
    }

    /**
     * Posts an event to the bus.
     *
     * @param event the event object
     */
    public final void post(Object event) {
        mBus.onNext(event);
    }

    /**
     * Posts an sticky event to the bus.
     *
     * @param event the event object
     */
    public final void postSticky(Object event) {
        synchronized (mStickyEventMap) {
            mStickyEventMap.put(event.getClass(), event);
            post(event);
        }
    }

    /**
     * Registers an action for specified event type.
     * <p><b>NOTE: Unsubscribe the subscription when you need not the event.</b></p>
     * Example:
     * <pre>
     *     rxSubscription = RxBus.getDefault().subscribe(SomeEvent.class, e -> {
     *         // do something
     *     })
     *
     *     // when stop receive the event
     *     if(!rxSubscription.isUnsubscribed()) {
     *         rxSubscription.unsubscribe();
     *     }
     * </pre>
     *
     * @param eventType the class of event type
     * @param action    the action for event
     * @param <T>       the type of event
     * @return the subscription for this action
     */
    public final <T> Subscription subscribe(Class<T> eventType, Action1<T> action) {
        return toObservable(eventType).subscribe(action);
    }

    /**
     * Likes {@code subscribe} but for sticky event.
     *
     * @param eventType the class of event type
     * @param action    the action for event
     * @param <T>       the type of event
     * @return the subscription for this action
     */
    public final <T> Subscription subscribeSticky(Class<T> eventType, Action1<T> action) {
        return toObservableSticky(eventType).subscribe(action);
    }

    /**
     * Gets the observable for specified event type.
     *
     * @param eventType the class of event type
     * @param <T>       the type of event
     * @return the observable
     */
    public final <T> Observable<T> toObservable(Class<T> eventType) {
        return mBus.ofType(eventType);
    }

    /**
     * Gets the observable for specified sticky event type.
     *
     * @param eventType the class of event type
     * @param <T>       the type of event
     * @return the observable
     */
    public final <T> Observable<T> toObservableSticky(final Class<T> eventType) {
        synchronized (mStickyEventMap) {
            val observable = mBus.ofType(eventType);
            val event = mStickyEventMap.get(eventType);
            if (event != null) {
                return observable.mergeWith(Observable.create(subscriber -> {
                    subscriber.onNext(eventType.cast(event));
                }));
            } else {
                return observable;
            }
        }
    }

    /**
     * Gets a sticky event for specified type.
     *
     * @param eventType the class of event type
     * @param <T>       the type of event
     * @return the event object, or {@literal null} if not found
     */
    public final <T> T getStickyEvent(Class<T> eventType) {
        synchronized (mStickyEventMap) {
            return eventType.cast(mStickyEventMap.get(eventType));
        }
    }

    /**
     * Removes sticky event for specified event type.
     *
     * @param eventType the class of event type
     * @param <T>       the type of event
     * @return current event object, of {@literal null} if not found
     */
    public final <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (mStickyEventMap) {
            return eventType.cast(mStickyEventMap.remove(eventType));
        }
    }

    /**
     * Removes all sticky events.
     */
    public final void removeAllStickyEvents() {
        synchronized (mStickyEventMap) {
            mStickyEventMap.clear();
        }
    }
}
