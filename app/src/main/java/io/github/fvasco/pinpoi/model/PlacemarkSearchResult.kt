package io.github.fvasco.pinpoi.model

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * @author Placemark result with annotation information.
 *         Used by [io.github.fvasco.pinpoi.dao.PlacemarkDao.findAllPlacemarkNear]
 */
class PlacemarkSearchResult(
    val id: Long = 0,
    override val coordinates: Coordinates,
    override val name: String,
    val flagged: Boolean,
    val hasNote: Boolean,
    val collectionId: Long
) : PlacemarkBase
