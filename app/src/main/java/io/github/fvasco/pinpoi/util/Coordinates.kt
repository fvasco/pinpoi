package io.github.fvasco.pinpoi.util

import android.location.Location
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/**
 * Simple coordinates

 * @author Francesco Vasco
 */
data class Coordinates(val latitude: Float, val longitude: Float) {

    fun distanceTo(other: Coordinates): Float {
        val result = FloatArray(1)
        Location.distanceBetween(latitude.toDouble(), longitude.toDouble(), other.latitude.toDouble(), other.longitude.toDouble(), result)
        return result[0]
    }

    override fun toString(): String = synchronized(DECIMAL_FORMAT) {
        return DECIMAL_FORMAT.format(latitude) + ',' + DECIMAL_FORMAT.format(longitude)
    }


    companion object {
        val EMPTY = Coordinates(Float.NaN, Float.NaN)
        private val DECIMAL_FORMAT = DecimalFormat("###.######", DecimalFormatSymbols(Locale.ENGLISH))
    }
}
