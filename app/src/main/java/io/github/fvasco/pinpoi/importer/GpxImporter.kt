package io.github.fvasco.pinpoi.importer

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * GPX importer
 * @author Francesco Vasco
 */
class GpxImporter : AbstractXmlImporter() {

    override fun handleStartTag() {
        when (tag) {
            "wpt" -> {
                newPlacemark()
                placemark!!.coordinates = Coordinates(java.lang.Float.parseFloat(parser.getAttributeValue(null, "lat")),
                        java.lang.Float.parseFloat(parser.getAttributeValue(null, "lon")))
            }
            "link" -> placemark?.let { placemark ->
                if (placemark.description.isBlank()) {
                    placemark.description = parser.getAttributeValue(null, "href") ?: ""
                }
            }
        }
    }

    override fun handleEndTag() {
        if (placemark == null) return
        when (tag) {
            "wpt" -> importPlacemark()
            "name" -> placemark?.name = text
            "desc" -> placemark?.description = text
        }
    }
}
