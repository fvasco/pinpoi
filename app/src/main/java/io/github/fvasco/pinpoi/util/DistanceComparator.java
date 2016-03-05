package io.github.fvasco.pinpoi.util;

import android.location.Location;

import java.util.Comparator;

/**
 * Compare placemark using distance from a specific {@linkplain Coordinates}
 *
 * @author Francesco Vasco
 */
public class DistanceComparator implements Comparator<Coordinates> {

    private final Coordinates center;
    private final float[] distanceResult = new float[1];

    public DistanceComparator(final Coordinates center) {
        Util.requireNonNull(center);
        this.center = center;
    }

    @Override
    public int compare(Coordinates lhs, Coordinates rhs) {
        int res = Double.compare(calculateDistance(lhs), calculateDistance(rhs));
        if (res == 0) {
            // equals <==> same coordinates
            res = Float.compare(lhs.getLatitude(), rhs.getLatitude());
            if (res == 0) res = Float.compare(lhs.getLongitude(), rhs.getLongitude());
        }
        return res;
    }

    /**
     * Calculate distance to placemark
     * {@see Location@distanceTo}
     */
    public double calculateDistance(final Coordinates p) {
        Location.distanceBetween(center.getLatitude(), center.getLongitude(), p.getLatitude(), p.getLongitude(), distanceResult);
        return distanceResult[0];
    }

}
