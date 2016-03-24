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
                    p.coordinates = Coordinates(java.lang.Float.parseFloat(parts[0]), java.lang.Float.parseFloat(parts[1]))
                }
            }

        // RSS
            "lat" -> p.coordinates = p.coordinates.copy(latitude = java.lang.Float.parseFloat(text))
            "long" -> p.coordinates = p.coordinates.copy(longitude = java.lang.Float.parseFloat(text))
        }
    }
}
