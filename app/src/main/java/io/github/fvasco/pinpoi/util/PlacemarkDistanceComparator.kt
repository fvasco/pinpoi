package io.github.fvasco.pinpoi.util

import io.github.fvasco.pinpoi.model.PlacemarkSearchResult

/**
 * Compare placemark using distance from a specific [Coordinates]

 * @author Francesco Vasco
 */
class PlacemarkDistanceComparator(private val center: Coordinates) :
    Comparator<PlacemarkSearchResult> {

    override fun compare(plhs: PlacemarkSearchResult, prhs: PlacemarkSearchResult): Int {
        val lhs = plhs.coordinates
        val rhs = prhs.coordinates
        var res = center.distanceTo(lhs).compareTo(center.distanceTo(rhs))
        if (res == 0) {
            // equals <==> same coordinates
            res = lhs.latitude.compareTo(rhs.latitude)
            if (res == 0) res = lhs.longitude.compareTo(rhs.longitude)
        }
        return res
    }
}
