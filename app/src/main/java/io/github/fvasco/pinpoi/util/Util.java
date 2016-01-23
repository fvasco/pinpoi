package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.location.Address;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.widget.Toast;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Common utility for multi thread develop
 *
 * @author Francesco Vasco
 */
public final class Util {
    public static final Handler MAIN_LOOPER_HANDLER = new Handler(Looper.getMainLooper());
    public static final ExecutorService EXECUTOR =
            Executors.unconfigurableExecutorService(Executors.newScheduledThreadPool(3));
    private static Context APPLICATION_CONTEXT;

    private Util() {
    }

    public static void initApplicationContext(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (APPLICATION_CONTEXT != context) {
            APPLICATION_CONTEXT = context;
        }
    }

    public static Context getApplicationContext() {
        assert APPLICATION_CONTEXT != null;
        return APPLICATION_CONTEXT;
    }

    public static Location newLocation(double latitude, double longitude) {
        final Location l = new Location(Util.class.getSimpleName());
        l.setLatitude(latitude);
        l.setLongitude(longitude);
        l.setAccuracy(0);
        l.setTime(System.currentTimeMillis());
        return l;
    }

    public static String formatCoordinate(@NonNull final Placemark placemark) {
        return Float.toString(placemark.getLatitude())
                + ',' + Float.toString(placemark.getLongitude());
    }

    public static void showToast(@NonNull final CharSequence message, final int duration) {
        MAIN_LOOPER_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(APPLICATION_CONTEXT, message, duration).show();
            }
        });
    }

    /**
     * Check if string is empty or null
     *
     * @return true if string {@linkplain String#isEmpty()} or null
     */
    public static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    public static boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }

    /**
     * Safe trim for string, null check
     */
    public static String trim(String text) {
        return text == null ? null : text.trim();
    }

    /**
     * Append text (if present) to string builder using a separator (if present)
     */
    public static void append(CharSequence text, CharSequence separator, @NonNull StringBuilder stringBuilder) {
        if (!isEmpty(text)) {
            if (stringBuilder.length() > 0 && separator != null) {
                stringBuilder.append(separator);
            }
            stringBuilder.append(text);
        }
    }

    /**
     * Convert an {@linkplain Address} to a string
     */
    public static String toString(Address a) {
        if (a == null) {
            return null;
        }
        if (a.getMaxAddressLineIndex() == 0) {
            return a.getAddressLine(0);
        } else if (a.getMaxAddressLineIndex() > 0) {
            final StringBuilder stringBuilder = new StringBuilder(a.getMaxAddressLineIndex());
            for (int i = 1; i <= a.getMaxAddressLineIndex(); ++i) {
                stringBuilder.append(", ").append(a.getAddressLine(i));
            }
            return stringBuilder.toString();
        } else {
            final StringBuilder stringBuilder = new StringBuilder();
            final String separator = ", ";
            append(a.getFeatureName(), separator, stringBuilder);
            append(a.getLocality(), separator, stringBuilder);
            append(a.getAdminArea(), separator, stringBuilder);
            append(a.getCountryCode(), separator, stringBuilder);
            return isEmpty(stringBuilder) ? a.toString() : stringBuilder.toString();
        }
    }
}
