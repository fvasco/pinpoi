package io.github.fvasco.pinpoi.util

import android.app.ActionBar.LayoutParams
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import io.github.fvasco.pinpoi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL
import java.util.regex.Pattern

const val DEBUG = false

private val HTML_PATTERN =
    Pattern.compile("<(\\w+)(\\s[^<>]*)?>.*<\\/\\1>|<\\w+(\\s[^<>]*)?/>", Pattern.DOTALL)

/**
 * Check value and throw assertion error if false
 */
fun assertDebug(check: Boolean, value: Any? = null) {
    if (DEBUG && !check)
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

// Initialize the Google Mobile Ads SDK on a background thread.
fun Context.initAdMob(adViewContainer: ViewGroup, adUnitId: String, adSize: AdSize) {
    if (DEBUG) {
        val testDeviceIds = listOf("33BE2250B43518CCDA7DE426D04EE231")
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)
    }

    // Create a new ad view.
    val adView = AdView(this)
    adView.adUnitId =
        if (DEBUG) "ca-app-pub-3940256099942544/9214589741"
        else adUnitId
    adView.setAdSize(adSize)

    // create hidden "Advertising" text
    val advertisingText = TextView(this)
    advertisingText.text = getString(R.string.advertising)
    advertisingText.visibility = View.GONE
    advertisingText.setTextColor(Color.BLACK)
    advertisingText.setBackgroundColor(Color.WHITE)

    // create the adview container
    val frameLayout = FrameLayout(this)
    frameLayout.setBackgroundColor(Color.WHITE)
    frameLayout.layoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    frameLayout.addView(adView)

    // Replace ad container with new ad view.
    adViewContainer.removeAllViews()
    adViewContainer.addView(advertisingText)
    adViewContainer.addView(frameLayout)

    // show text when advertising starts
    adView.adListener = object : AdListener() {
        override fun onAdLoaded() {
            advertisingText.visibility = View.VISIBLE
        }
    }

    // Start loading the ad in the background.
    val adRequest = AdRequest.Builder().build()
    adView.loadAd(adRequest)
    GlobalScope.launch(Dispatchers.IO) {
        MobileAds.initialize(this@initAdMob) {}
    }
}

/**
 * Create an usable URL
 */
fun makeURL(url: String): URL {
    val spec = when {
        url.startsWith("/") -> "file://$url"
        url.startsWith("http://") -> "https${url.substring(4)}"
        else -> url
    }
    return URL(spec)
}
