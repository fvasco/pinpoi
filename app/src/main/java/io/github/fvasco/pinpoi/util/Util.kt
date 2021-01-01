package io.github.fvasco.pinpoi.util

import io.github.fvasco.pinpoi.BuildConfig
import java.util.regex.Pattern

private val HTML_PATTERN = Pattern.compile("<(\\w+)(\\s[^<>]*)?>.*<\\/\\1>|<\\w+(\\s[^<>]*)?/>", Pattern.DOTALL)

/**
 * Check value and throw assertion error if false
 */
fun assertDebug(check: Boolean, value: Any? = null) {
    if (BuildConfig.DEBUG && !check)
        throw AssertionError(value?.toString())
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
 * Switch a HTTP URL to HTTPS protocol
 */
fun httpToHttps(url: String) =
    if (url.startsWith("http://")) "https${url.substring(4)}"
    else url

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
