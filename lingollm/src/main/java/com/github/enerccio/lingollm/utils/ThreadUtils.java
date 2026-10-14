package com.github.enerccio.lingollm.utils;

/**
 * Runs the runnable in a fresh thread (so no thread locals - Vaadin current instances, request attributes - of the
 * caller leak into it) and waits for it to finish.
 */
public class ThreadUtils {

    public static void executeInThread(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}