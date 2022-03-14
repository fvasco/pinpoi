package io.github.fvasco.pinpoi.importer

import android.util.Log
import io.github.fvasco.pinpoi.util.Coordinates
import io.github.fvasco.pinpoi.util.makeURL
import java.io.IOException

/**
 * KML importer
 * @author Francesco Vasco
 */
class KmlImporter : AbstractXmlImporter() {

    private var latitude: Float = Float.NaN
    private var longitude: Float = Float.NaN

    override fun handleStartTag() {
        when (tag) {
            "Placemark" -> {
                newPlacemark()
                latitude = Float.NaN
                longitude = Float.NaN
            }
        }
    }

    @Throws(IOException::class)
    override fun handleEndTag() {
        if (placemark == null) {
            if ("href" == tag && checkCurrentPath("kml", "Document", "NetworkLink", "Url")) {
                val href = text
                Log.e(KmlImporter::class.java.simpleName, "NetworkLink href $href")
                makeURL(href).openStream().use { inputStream ->
                    val delegateImporter = KmlImporter()
                    delegateImporter.configureFrom(this)
                    delegateImporter.importPlacemarks(inputStream)
                }
            }
        } else {
            val p = placemark ?: return
            when (tag) {
                "Placemark" -> {
                    if (!latitude.isNaN() && !longitude.isNaN()) {
                        // set placemark to center
                        p.coordinates = Coordinates(longitude = longitude, latitude = latitude)
                        importPlacemark()
                    }
                    placemark = null
                }
                "name" -> p.name = text
                "description" -> p.description = text
                "coordinates" -> // read multiple lines if present (point, line, polygon)
                    for (line in text.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }) {
                        // format: longitude, latitude, altitude
                        val coordinates = line.split(',', limit = 3).takeIf { it.size in 2..3 }
                            ?: continue
                        val lon = coordinates[0].toFloatOrNull() ?: continue
                        val lat = coordinates[1].toFloatOrNull() ?: continue
                        latitude = lat
                        longitude = lon
                        break
                    }
            }
        }
    }
}