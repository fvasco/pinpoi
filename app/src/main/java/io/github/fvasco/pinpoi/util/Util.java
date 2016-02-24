package io.github.fvasco.pinpoi.util;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.BuildConfig;
import android.util.Log;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Miscellaneous common utility
 *
 * @author Francesco Vasco
 */
public final class Util {
    public static final Handler MAIN_LOOPER_HANDLER = new Handler(Looper.getMainLooper());
    public static final ExecutorService EXECUTOR =
            Executors.unconfigurableExecutorService(Executors.newScheduledThreadPool(3));
    public static final XmlPullParserFactory XML_PULL_PARSER_FACTORY;
    private static final Pattern HTML_PATTERN = Pattern.compile("<(\\w+)(\\s[^<>]*)?>.*<\\/\\1>|<\\w+(\\s[^<>]*)?/>", Pattern.DOTALL);
    private static Context APPLICATION_CONTEXT;

    static {
        HttpURLConnection.setFollowRedirects(true);
        try {
            XML_PULL_PARSER_FACTORY = XmlPullParserFactory.newInstance();
            XML_PULL_PARSER_FACTORY.setNamespaceAware(true);
            XML_PULL_PARSER_FACTORY.setValidating(false);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
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
    public static boolean isEmpty(final CharSequence text) {
        return text == null || text.length() == 0;
    }

    public static boolean isEmpty(final Collection c) {
        return c == null || c.isEmpty();
    }

    /**
     * Try to detect HTML text
     */
    public static boolean isHtml(final CharSequence text) {
        return text != null && HTML_PATTERN.matcher(text).find();
    }

    /**
     * Check if text is a uri
     */
    public static boolean isUri(final String text) {
        return text != null && text.matches("\\w+:/{1,3}\\w+.+");
    }

    /**
     * Escape text for Javascript
     */
    public static CharSequence escapeJavascript(final CharSequence text) {
        final StringBuilder out = new StringBuilder(text.length() + text.length() / 2);
        for (int i = 0, max = text.length(); i < max; ++i) {
            final char c = text.charAt(i);
            switch (c) {
                // C escape
                case '\'':
                case '\"':
                case '\\':
                case '/':
                    out.append('\\').append(c);
                    break;

                // html escape
                case '<':
                case '>':
                case '&':
                    out.append("&#x").append(Integer.toHexString(c)).append(';');
                    break;

                case '\b':
                    out.append("\\b");
                case '\f':
                    out.append("\\f");
                case '\n':
                    out.append("\\n");
                case '\t':
                    out.append("\\t");
                case '\r':
                    out.append("\\r");

                default:
                    out.append(c);
            }
        }
        return out;
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
     * Show indeterminate progress dialog and execute runnable in background
     *
     * @param title    progress dialog title
     * @param message  progress dialog message
     * @param runnable task to execute in background
     * @param context  dialog context
     */
    public static void showProgressDialog(final CharSequence title, final CharSequence message,
                                          final Runnable runnable, final Context context) {
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(title);
        progressDialog.setMessage(message);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
        Util.EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(Util.class.getSimpleName(), "showProgressDialog begin: " + title);
                    runnable.run();
                } finally {
                    Log.i(Util.class.getSimpleName(), "showProgressDialog end: " + title);
                    progressDialog.dismiss();
                }
            }
        });
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

    /**
     * Copy stream to another
     */
    public static void copy(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, count);
        }
    }
}
