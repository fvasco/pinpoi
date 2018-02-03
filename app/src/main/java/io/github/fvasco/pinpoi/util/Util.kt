package io.github.fvasco.pinpoi.util

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.support.v7.app.AppCompatDelegate
import android.text.Html
import android.util.Log
import io.github.fvasco.pinpoi.BuildConfig
import org.jetbrains.anko.async
import org.jetbrains.anko.longToast
import org.jetbrains.anko.onUiThread
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.net.HttpURLConnection
import java.util.regex.Pattern

/**
 * Miscellaneous common utility

 * @author Francesco Vasco
 */
object Util {
    val XML_PULL_PARSER_FACTORY: XmlPullParserFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }
    }

    init {
        HttpURLConnection.setFollowRedirects(true)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            // avoid "invalid drawable tag vector" on kitkat
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }

    lateinit var applicationContext: Context
}

private val HTML_PATTERN = Pattern.compile("<(\\w+)(\\s[^<>]*)?>.*<\\/\\1>|<\\w+(\\s[^<>]*)?/>", Pattern.DOTALL)

/**
 * Check value and throw assertion error if false
 */
fun assertDebug(check: Boolean, value: Any? = null) {
    if (BuildConfig.DEBUG && !check)
        throw AssertionError(value?.toString())
}

fun showToast(throwable: Throwable) {
    Util.applicationContext.onUiThread {
        Util.applicationContext.longToast(throwable.message ?: "Error ${throwable.javaClass.simpleName}")
    }
}

/**
 * Try to detect HTML text
 */
fun CharSequence?.isHtml(): Boolean {
    return this != null && HTML_PATTERN.matcher(this).find()
}

/**
 * Check if text is a uri
 */
fun CharSequence?.isUri(): Boolean {
    return this?.matches("\\w{3,5}:/{1,3}\\w.+".toRegex()) ?: false
}

/**
 * Escape text for HTML
 */
fun escapeHtml(text: CharSequence): CharSequence {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        return Html.escapeHtml(text)
    } else {
        val out = StringBuilder(text.length + text.length / 4)
        for (c in text) {
            when (c) {
            // html escape
                '<', '>', '&', '\'', '\"' -> out.append("&#x").append(Integer.toHexString(c.toInt())).append(';')
                else -> out.append(c)
            }
        }
        return out
    }
}

/**
 * Escape text for Javascript
 */
fun escapeJavascript(text: CharSequence): CharSequence {
    val out = StringBuilder(text.length + text.length / 3)
    for (c in text) {
        when (c) {
        // C escape
            '\'', '\"', '\\', '/' -> out.append('\\').append(c)

        // html escape
            '<', '>', '&' -> out.append("&#x").append(Integer.toHexString(c.toInt())).append(';')

        // special characters escape
            '\b' -> out.append("\\b")
            '\n' -> out.append("\\n")
            '\t' -> out.append("\\t")
            '\r' -> out.append("\\r")

            else -> out.append(c)
        }
    }
    return out
}

fun openFileChooser(dir: File, context: Context, fileConsumer: (File) -> Unit) {
    val parentDir = dir.parentFile
    if (parentDir == null || dir.isDirectory && dir.canRead()) {
        val files = dir.listFiles { pathname ->
            pathname.canRead()
                    && !pathname.name.startsWith(".")
                    && (pathname.isFile || pathname.list()?.isEmpty() == false)
        } ?: emptyArray()
        val fileNames = ArrayList<String>(files.size + 1)
        // check permission/readability of parent file
        if (parentDir?.list()?.isEmpty() == false) {
            fileNames += ".."
        }
        for (file in files) {
            fileNames +=
                    if (file.isDirectory)
                        file.name + '/'
                    else
                        file.name
        }
        fileNames.sort()
        AlertDialog.Builder(context).setTitle(dir.absolutePath).setItems(fileNames.toTypedArray()) { dialog, which ->
            dialog.tryDismiss()
            val file = File(dir, fileNames[which]).absoluteFile.canonicalFile
            if (file.isDirectory) {
                openFileChooser(file, context, fileConsumer)
            } else {
                fileConsumer(file)
            }
        }.show()
    } else {
        openFileChooser(parentDir, context, fileConsumer)
    }
}

/**
 * Show indeterminate progress dialog and execute runnable in background
 * @param title    progress dialog title
 * @param message  progress dialog message
 * @param context  dialog context
 * @param runnable task to execute in background
 */
fun showProgressDialog(title: CharSequence, message: CharSequence?, context: Context,
                       runnable: () -> Unit) {
    val progressDialog = ProgressDialog(context)
    progressDialog.setTitle(title)
    progressDialog.setMessage(message)
    progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
    progressDialog.isIndeterminate = true
    progressDialog.setCancelable(false)
    progressDialog.setCanceledOnTouchOutside(false)
    progressDialog.show()
    context.async {
        try {
            Log.i(Util::class.java.simpleName, "showProgressDialog begin: $title")
            runnable()
        } catch (e: Exception) {
            progressDialog.tryDismiss()
            Log.e(Util::class.java.simpleName, "showProgressDialog error $title", e)
            showToast(e)
        } finally {
            Log.i(Util::class.java.simpleName, "showProgressDialog end: $title")
            progressDialog.tryDismiss()
        }
    }
}

fun DialogInterface.tryDismiss() {
    Util.applicationContext.onUiThread {
        try {
            if (this !is Dialog || isShowing)
                dismiss()
        } catch (e: Exception) {
            Log.w(DialogInterface::tryDismiss.javaClass.canonicalName, "Error on dialog dismiss", e)
            e.printStackTrace()
        }
    }
}

/**
 * Append text (if present) to string builder using a separator (if present)
 */
fun append(text: CharSequence, separator: CharSequence?, stringBuilder: StringBuilder) {
    if (text.isNotEmpty()) {
        if (stringBuilder.isNotEmpty() && separator != null) {
            stringBuilder.append(separator)
        }
        stringBuilder.append(text)
    }
}
