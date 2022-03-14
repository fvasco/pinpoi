package io.github.fvasco.pinpoi.importer

/**
 * Filter preference for file import
 */
enum class FileFormatFilter(val validExtensions: Set<String>) {
    NONE(setOf()),
    CSV_LAT_LON(setOf("asc", "csv", "txt")), CSV_LON_LAT(setOf("asc", "csv", "txt")),
    GEOJSON(setOf("json", "geojson")),
    GPX(setOf("gpx")),
    KML(setOf("kml")),
    OV2(setOf("ov2"))
}
