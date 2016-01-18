package io.github.fvasco.pinpoi.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Common utility for multi thread develop
 *
 * @author Francesco Vasco
 */
public final class ThreadUtil {

    public static final ExecutorService EXECUTOR =
            Executors.unconfigurableExecutorService(Executors.newScheduledThreadPool(3));

    private ThreadUtil() {
    }
}
