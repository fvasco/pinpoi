package io.github.fvasco.pinpoi.model

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * Container for placemark

 * @author Francesco Vasco
 */
data class Placemark(var id: Long = 0, override var name: String = "", var description: String = "",
                     override var coordinates: Coordinates = Coordinates.EMPTY,
                     var collectionId: Long = 0) : PlacemarkBase
