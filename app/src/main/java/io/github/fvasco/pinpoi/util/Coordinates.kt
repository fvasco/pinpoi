package io.github.fvasco.pinpoi.util

import android.location.Location
import org.osmdroid.util.GeoPoint
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Simple coordinates

 * @author Francesco Vasco
 */
data class Coordinates(val latitude: Float, val longitude: Float) {

    override fun toString(): String = synchronized(DECIMAL_FORMAT) {
        return DECIMAL_FORMAT.format(latitude) + ',' + DECIMAL_FORMAT.format(longitude)
    }


    companion object {
        val EMPTY = Coordinates(Float.NaN, Float.NaN)
        private val DECIMAL_FORMAT =
            DecimalFormat("###.######", DecimalFormatSymbols(Locale.ENGLISH))
    }
}

fun Coordinates.distanceTo(other: Coordinates): Float {
    val result = FloatArray(1)
    Location.distanceBetween(
        latitude.toDouble(),
        longitude.toDouble(),
        other.latitude.toDouble(),
        other.longitude.toDouble(),
        result
    )
    return result[0]
}

fun Coordinates.toGeoPoint() = GeoPoint(latitude.toDouble(), longitude.toDouble())
