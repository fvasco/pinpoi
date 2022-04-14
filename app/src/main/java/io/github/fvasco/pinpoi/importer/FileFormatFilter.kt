package io.github.fvasco.pinpoi.importer

/**
 * Filter preference for file import
 */
enum class FileFormatFilter(val validExtensions: Set<String>, val validMimeTypes: Set<String>) {
    NONE(setOf(), setOf()),
    CSV_LAT_LON(setOf("asc", "csv", "txt"), setOf("text/csv")),
    CSV_LON_LAT(setOf("asc", "csv", "txt"), setOf("text/csv")),
    GEOJSON(setOf("json", "geojson"), setOf("application/geo+json", "application/json")),
    GPX(setOf("gpx"), setOf("application/gpx+xml")),
    KML(setOf("kml"), setOf("application/vnd.google-earth.kml+xml")),
    OV2(setOf("ov2"), setOf()),
    RSS(setOf(), setOf("application/rss+xml"))
}
