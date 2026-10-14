package com.github.enerccio.lingollm.concurrent;

import com.github.enerccio.lingollm.Configuration;

public class AsyncRunnableWrapper {
    private final Throwable stackframe;

    public AsyncRunnableWrapper(Configuration configuration) {
        if (configuration != null && configuration.isSaveAsyncStacks()) {
            this.stackframe = new AsyncException("Async Task Submission Origin");
        } else {
            this.stackframe = null;
        }
    }

    public void run(ThrowingRunnable runnable) throws Throwable {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (stackframe != null) {
                t.addSuppressed(stackframe);
            }
            throw t;
        }
    }

    public static class AsyncException extends RuntimeException {
        public AsyncException(String message) {
            super(message);
        }
    }
}