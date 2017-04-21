package io.github.fvasco.pinpoi.model

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * Placemark base information
 *
 * @author Francesco Vasco
 */
interface PlacemarkBase {

    val name: String

    val coordinates: Coordinates
}
