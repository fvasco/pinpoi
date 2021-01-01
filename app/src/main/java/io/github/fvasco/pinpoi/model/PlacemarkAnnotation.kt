package io.github.fvasco.pinpoi.model

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * A user annotation on [Placemark]
 *
 * @author Francesco Vasco
 */
data class PlacemarkAnnotation(
    var coordinates: Coordinates = Coordinates.EMPTY,
    var note: String = "",
    var flagged: Boolean = false
)
