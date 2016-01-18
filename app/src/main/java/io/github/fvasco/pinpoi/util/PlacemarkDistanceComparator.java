package io.github.fvasco.pinpoi.util;

import android.location.Location;

import java.util.Comparator;
import java.util.Objects;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Compare placemark using distance from a specific {@linkplain android.location.Location}
 *
 * @author Francesco Vasco
 */
public class PlacemarkDistanceComparator implements Comparator<Placemark> {

    private final Location center;
    private final float[] distanceResult = new float[1];

    public PlacemarkDistanceComparator(final Location center) {
        Objects.requireNonNull(center);
        this.center = center;
    }

    @Override
    public int compare(Placemark lhs, Placemark rhs) {
        int res = Double.compare(calculateDistance(lhs), calculateDistance(rhs));
        if (res == 0)
            res = Long.compare(lhs.getId(), rhs.getId());
        return res;
    }

    /**
     * Calculate distance to placemark
     * {@see Location@distanceTo}
     */
    public double calculateDistance(final Placemark p) {
        Location.distanceBetween(center.getLatitude(), center.getLongitude(), p.getLatitude(), p.getLongitude(), distanceResult);
        return distanceResult[0];
    }

}
