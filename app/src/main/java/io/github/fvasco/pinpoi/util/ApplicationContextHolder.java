package io.github.fvasco.pinpoi.util;

import android.content.Context;

/**
 * Holds application context
 *
 * @author Francesco Vasco
 */
public class ApplicationContextHolder {
    private static Context CONTEXT;

    private ApplicationContextHolder() {
    }

    public static void init(Context context) {
        if (CONTEXT == null) {
            CONTEXT = context;
        }
    }

    public static Context get() {
        return CONTEXT;
    }
}
