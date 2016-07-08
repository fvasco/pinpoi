package io.github.fvasco.pinpoi.model

import io.github.fvasco.pinpoi.importer.FileFormatFilter

/**
 * A collection, aggregator for [Placemark]

 * @author Francesco Vasco
 */
data class PlacemarkCollection(
        var id: Long = 0,
        var name: String = "",
        var description: String = "",
        var category: String = "",
        var source: String = "",
        var fileFormatFilter: FileFormatFilter = FileFormatFilter.NONE,
        /**
         * Last collection update, unix time
         */
        var lastUpdate: Long = 0,
        var poiCount: Int = 0
)