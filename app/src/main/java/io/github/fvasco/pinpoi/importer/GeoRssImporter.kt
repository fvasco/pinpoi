package io.github.fvasco.pinpoi.importer

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * Import simple geo RSS/Atom

 * @author Francesco Vasco
 */
class GeoRssImporter : AbstractXmlImporter() {
    override fun handleStartTag() {
        when (tag) {
            "item" // RSS
                , "entry" // ATOM
            -> newPlacemark()
        }
    }

    override fun handleEndTag() {
        val p = placemark ?: return
        when (tag) {
            "item" // RSS
                , "entry" // ATOM
            -> importPlacemark()

            "title" -> p.name = text

            "description" // Atom
                , "summary" // RSS
            -> p.description = text

            "point" // Atom
            -> {
                val parts = text.split("[ ,]+".toRegex(), 3).toTypedArray()
                if (parts.size >= 2) {
                    p.coordinates = Coordinates(parts[0].toFloat(), parts[1].toFloat())
                }
            }

        // RSS
            "lat" -> p.coordinates = p.coordinates.copy(latitude = text.toFloat())
            "long" -> p.coordinates = p.coordinates.copy(longitude = text.toFloat())
        }
    }
}
