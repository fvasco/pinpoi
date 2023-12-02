package io.github.fvasco.pinpoi.importer

import android.text.Html
import android.text.TextUtils
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
        val features = json.optJSONArray("features") ?: return
        repeat(features.length()) { i ->
            val feature = features.getJSONObject(i)
            parse(feature)
        }
    }

    private fun parseFeature(json: JSONObject) {
        val properties = json.optJSONObject("properties") ?: return
        val geometry = json.optJSONObject("geometry") ?: return
        val name = findName(properties) ?: findName(json) ?: return

        val description = buildString {
            properties.keys().asSequence().sorted().forEach { key ->
                if (!properties.isNull(key)) {
                    val value = properties[key]
                    append("<p>")
                    append("<b>", Html.escapeHtml(key), "</b>: ")
                    if (value is String && value.startsWith("https://")) {
                        append(
                            "<a href='",
                            TextUtils.htmlEncode(value),
                            "'>",
                            Html.escapeHtml(value),
                            "</a>"
                        )
                    } else {
                        append(Html.escapeHtml(value.toString()))
                    }
                    append("</p>")
                }
            }
        }

        val coordinate = findCoordinate(geometry.optJSONArray("coordinates")) ?: return

        if (name.isNotBlank()) {
            importPlacemark(
                Placemark(
                    name = name,
                    description = description,
                    coordinates = coordinate
                )
            )
        }
    }

    private fun findName(obj: JSONObject) =
        listOf(obj.optString("title"), obj.optString("name"), obj.optString("id"))
            .find(String::isNotBlank)

    private fun findCoordinate(array: JSONArray?): Coordinates? {
        if (array == null || array.length() == 0) return null
        if (array.optDouble(0).isNaN()) return findCoordinate(array.optJSONArray(0))
        // array: lon,lat[,alt]
        if (array.length() !in 2..3) return null
        return Coordinates(
            longitude = array.getDouble(0).toFloat(),
            latitude = array.getDouble(1).toFloat()
        )
    }
}
