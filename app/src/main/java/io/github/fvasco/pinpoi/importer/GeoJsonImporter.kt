package io.github.fvasco.pinpoi.importer

import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.util.Coordinates
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class GeoJsonImporter : AbstractImporter() {

    override fun importImpl(inputStream: InputStream) {
        val json = inputStream.use { it.reader().readText() }.let(::JSONObject)
        parse(json)
    }

    private fun parse(json: JSONObject) {
        when (json.optString("type")) {
            "Feature" -> parseFeature(json)
            "FeatureCollection" -> parseFeatureCollection(json)
        }
    }

    private fun parseFeatureCollection(json: JSONObject) {
        val features = json.getJSONArray("features") ?: return
        repeat(features.length()) { i ->
            val feature = features.getJSONObject(i)
            parse(feature)
        }
    }

    private fun parseFeature(json: JSONObject) {
        val name =
            json.optJSONObject("properties")?.optString("name")
                ?: return
        val geometry = json.optJSONObject("geometry") ?: return
        val coordinates = geometry.optJSONArray("coordinates")?.takeIf { it.length() > 0 } ?: return
        val coordinate = findCoordinate(coordinates)
        if (name.isNotBlank()) {
            importPlacemark(Placemark(name = name.trim(), coordinates = coordinate))
        }
    }

    private fun findCoordinate(array: JSONArray): Coordinates {
        if (array.optDouble(0).isNaN()) return findCoordinate(array.getJSONArray(0))
        // array: lon,lat[,alt]
        return Coordinates(
            longitude = array.getDouble(0).toFloat(),
            latitude = array.getDouble(1).toFloat()
        )
    }
}
