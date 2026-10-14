package com.github.enerccio.lingollm.domain.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Runnable onCancelCallback;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            Runnable callback = onCancelCallback;
            if (callback != null) {
                callback.run();
            }
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void setOnCancelCallback(Runnable callback) {
        this.onCancelCallback = callback;
    }

    /**
     * Adds a listener run when the token is cancelled (at once if it already is), besides the single callback of
     * {@link #setOnCancelCallback}.
     *
     * @return removes the listener again
     */
    public Runnable onCancel(Runnable listener) {
        listeners.add(listener);
        if (cancelled.get()) {
            listener.run();
        }
        return () -> listeners.remove(listener);
    }
}
