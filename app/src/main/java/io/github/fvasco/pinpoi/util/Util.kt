package io.github.fvasco.pinpoi.util

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.widget.Toast
import io.github.fvasco.pinpoi.BuildConfig
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Miscellaneous common utility

 * @author Francesco Vasco
 */
object Util {
    val MAIN_LOOPER_HANDLER = Handler(Looper.getMainLooper())
    val EXECUTOR = Executors.unconfigurableExecutorService(Executors.newScheduledThreadPool(3))
    val XML_PULL_PARSER_FACTORY: XmlPullParserFactory by lazy {
        val res = XmlPullParserFactory.newInstance()
        res.isNamespaceAware = true
        res.isValidating = false
        res
    }

    init {
        HttpURLConnection.setFollowRedirects(true)
    }

    lateinit var applicationContext: Context
}

private val HTML_PATTERN = Pattern.compile("<(\\w+)(\\s[^<>]*)?>.*<\\/\\1>|<\\w+(\\s[^<>]*)?/>", Pattern.DOTALL)

/**
 * Check value and thorow assertion errror if false
 */
inline fun assertDebug(check: Boolean, value: Any? = null) =
        if (BuildConfig.DEBUG && !check)
            throw AssertionError(value?.toString())
        else Unit;

fun showToast(throwable: Throwable) {
    showToast(throwable.message ?: "Error ${throwable.javaClass.simpleName}", Toast.LENGTH_LONG)
}

fun showToast(message: CharSequence, duration: Int) {
    if (io.github.fvasco.pinpoi.BuildConfig.DEBUG) {
        Log.i(Util::class.java.simpleName, message.toString())
    }
    Util.MAIN_LOOPER_HANDLER.post { Toast.makeText(Util.applicationContext, message, duration).show() }
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
    if (dir.isDirectory && dir.canRead()) {
        val files = dir.listFiles { pathname -> pathname.canRead() && !pathname.name.startsWith(".") }
        val fileNames = arrayOfNulls<String>(files.size + 1)
        // last is up dir
        fileNames[files.size] = ".."
        for (i in files.indices.reversed()) {
            val file = files[i]
            fileNames[i] = if (file.isDirectory)
                file.name + '/'
            else
                file.name
        }
        Arrays.sort(fileNames)
        AlertDialog.Builder(context).setTitle(dir.absolutePath).setItems(fileNames) { dialog, which ->
            dialog.dismiss()
            val file = File(dir, fileNames[which]).absoluteFile.canonicalFile
            if (file.isDirectory) {
                openFileChooser(file, context, fileConsumer)
            } else {
                fileConsumer(file)
            }
        }.show()
    } else {
        openFileChooser(dir.parentFile, context, fileConsumer)
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
    Util.EXECUTOR.submit {
        try {
            Log.i(Util::class.java.simpleName, "showProgressDialog begin: $title")
            runnable()
        } catch(e: Exception) {
            progressDialog.dismiss()
            Log.e(Util::class.java.simpleName, "showProgressDialog error $title", e)
            showToast(e)
        } finally {
            Log.i(Util::class.java.simpleName, "showProgressDialog end: $title")
            progressDialog.dismiss()
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
