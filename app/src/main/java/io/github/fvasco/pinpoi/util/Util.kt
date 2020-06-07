package io.github.fvasco.pinpoi.util

import android.content.Context
import io.github.fvasco.pinpoi.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

private val HTML_PATTERN = Pattern.compile("<(\\w+)(\\s[^<>]*)?>.*<\\/\\1>|<\\w+(\\s[^<>]*)?/>", Pattern.DOTALL)

/**
 * Check value and throw assertion error if false
 */
fun assertDebug(check: Boolean, value: Any? = null) {
    if (BuildConfig.DEBUG && !check)
        throw AssertionError(value?.toString())
}

fun Context.showToast(throwable: Throwable) {
    runOnUiThread {
        longToast(throwable.message ?: "Error ${throwable.javaClass.simpleName}", this)
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
 * Escape text for Javascript
 */
fun escapeJavascript(text: CharSequence) = buildString(text.length + text.length / 3) {
    for (c in text) {
        when (c) {
            // html escape
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '\'' -> append("&apos;")
            '"' -> append("&quot;")

            else -> append(c)
        }
    }
}

/**
 * Open an input stream for the [resource].
 *
 * Use HTTPS connection.
 */
fun openInputStream(resource: String): InputStream =
        if (resource.startsWith("/")) {
            val file = File(resource)
            BufferedInputStream(FileInputStream(file))
        } else {
            HttpURLConnection.setFollowRedirects(true)
            val url =
                    if (resource.startsWith("http://")) "https${resource.substring(4)}"
                    else resource
            URL(url).openConnection().inputStream
        }

/**
 * Append text (if present) to string builder using a separator (if present)
 */
fun append(text: CharSequence?, separator: CharSequence?, stringBuilder: StringBuilder) {
    if (!text.isNullOrEmpty()) {
        if (stringBuilder.isNotEmpty() && separator != null) {
            stringBuilder.append(separator)
        }
        stringBuilder.append(text)
    }
}
