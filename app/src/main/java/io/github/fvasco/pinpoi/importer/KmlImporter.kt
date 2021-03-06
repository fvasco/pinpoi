package io.github.fvasco.pinpoi.importer

import android.util.Log
import io.github.fvasco.pinpoi.util.Coordinates
import io.github.fvasco.pinpoi.util.httpToHttps
import java.io.IOException
import java.net.URL

/**
 * KML importer

 * @author Francesco Vasco
 */
class KmlImporter : AbstractXmlImporter() {

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var coordinateCount: Int = 0

    override fun handleStartTag() {
        when (tag) {
            "Placemark" -> {
                newPlacemark()
                latitude = 0.0
                longitude = 0.0
                coordinateCount = 0
            }
        }
    }

    @Throws(IOException::class)
    override fun handleEndTag() {
        if (placemark == null) {
            if ("href" == tag && checkCurrentPath("kml", "Document", "NetworkLink", "Url")) {
                val href = text
                Log.e(KmlImporter::class.java.simpleName, "NetworkLink href $href")
                URL(httpToHttps(href)).openStream().use { inputStream ->
                    val delegateImporter = KmlImporter()
                    delegateImporter.configureFrom(this)
                    delegateImporter.importPlacemarks(inputStream)
                }
            }
        } else {
            val p = placemark ?: return
            when (tag) {
                "Placemark" -> {
                    if (coordinateCount > 0) {
                        // set placemark to center
                        p.coordinates = Coordinates(
                            (latitude / coordinateCount.toDouble()).toFloat(),
                            (longitude / coordinateCount.toDouble()).toFloat()
                        )
                        importPlacemark()
                    } else placemark = null
                }
                "name" -> p.name = text
                "description" -> p.description = text
                "coordinates" -> // read multiple lines if present (point, line, polygon)
                    for (line in text.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }) {
                        // format: longitude, latitude, altitude
                        val coordinates = line.split(',', limit = 3).takeIf { it.size in 2..3 }
                            ?: continue
                        val lat = coordinates[0].toDoubleOrNull() ?: continue
                        val lon = coordinates[1].toDoubleOrNull() ?: continue
                        longitude += lat
                        latitude += lon
                        ++coordinateCount
                    }
            }
        }
    }
}