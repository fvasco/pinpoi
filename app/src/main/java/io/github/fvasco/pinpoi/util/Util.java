package io.github.fvasco.pinpoi.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.BuildConfig;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Miscellaneous common utility
 *
 * @author Francesco Vasco
 */
public final class Util {
    public static final Handler MAIN_LOOPER_HANDLER = new Handler(Looper.getMainLooper());
    public static final ExecutorService EXECUTOR =
            Executors.unconfigurableExecutorService(Executors.newScheduledThreadPool(3));
    private static Context APPLICATION_CONTEXT;

    static {
        HttpURLConnection.setFollowRedirects(true);
    }

    private Util() {
    }

    public static void initApplicationContext(@NonNull Context context) {
        Objects.requireNonNull(context);
        //noinspection PointlessBooleanExpression
        if (BuildConfig.DEBUG && APPLICATION_CONTEXT != null && APPLICATION_CONTEXT != context) {
            throw new AssertionError();
        }
        APPLICATION_CONTEXT = context;
    }

    @NonNull
    public static Context getApplicationContext() {
        Objects.requireNonNull(APPLICATION_CONTEXT, "No context defined");
        return APPLICATION_CONTEXT;
    }

    public static void showToast(@NonNull final Throwable throwable) {
        final String message = throwable.getLocalizedMessage();
        showToast(isEmpty(message) ? "Error" : message, Toast.LENGTH_LONG);
    }

    public static void showToast(@NonNull final CharSequence message, final int duration) {
        if (io.github.fvasco.pinpoi.BuildConfig.DEBUG) {
            Log.i(Util.class.getSimpleName(), message.toString());
        }
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
     * Check if text is a uri
     */
    public static boolean isUri(String text) {
        return text != null && text.matches("\\w+:/{1,3}\\w+.+");
    }

    public static void openFileChooser(final File dir, final Consumer<File> fileConsumer, final Context context) {
        if (dir.isDirectory() && dir.canRead()) {
            File[] files = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.canRead() && !pathname.getName().startsWith(".");
                }
            });
            final String[] fileNames = new String[files.length + 1];
            // last is up dir
            fileNames[files.length] = "..";
            for (int i = files.length - 1; i >= 0; --i) {
                final File file = files[i];
                fileNames[i] = file.isDirectory()
                        ? file.getName() + '/'
                        : file.getName();
            }
            Arrays.sort(fileNames);
            new AlertDialog.Builder(context)
                    .setTitle(dir.getAbsolutePath())
                    .setItems(fileNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            final File file = new File(dir, fileNames[which]).getAbsoluteFile();
                            if (file.isDirectory()) {
                                openFileChooser(file, fileConsumer, context);
                            } else {
                                fileConsumer.accept(file);
                            }
                        }
                    }).show();
        } else {
            openFileChooser(dir.getParentFile(), fileConsumer, context);
        }
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
     * Force skip bytes
     */
    public static void skip(final InputStream inputStream, int skip) throws IOException {
        while (skip > 0) {
            skip -= inputStream.skip(skip);
        }
    }
}
