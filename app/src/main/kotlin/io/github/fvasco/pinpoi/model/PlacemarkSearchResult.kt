package io.github.fvasco.pinpoi.model

import io.github.fvasco.pinpoi.util.Coordinates

/**
 * @author Placemark result with annotation information.
 * *         Used by [io.github.fvasco.pinpoi.dao.PlacemarkDao.findAllPlacemarkNear]
 */
class PlacemarkSearchResult(var id: Long = 0,
                            override var coordinates: Coordinates,
                            override var name: String = "", var isFlagged: Boolean = false) :
        PlacemarkBase
