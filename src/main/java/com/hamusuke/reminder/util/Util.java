package com.hamusuke.reminder.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Util {
    public static void shutdownExecutor(final ExecutorService e) {
        e.shutdown();

        boolean f;
        try {
            f = e.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            f = false;
        }

        if (!f) {
            e.shutdownNow();
        }
    }
}
