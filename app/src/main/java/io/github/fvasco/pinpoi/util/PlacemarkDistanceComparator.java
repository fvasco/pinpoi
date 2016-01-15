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
    private final Location other;

    public PlacemarkDistanceComparator(final Location center) {
        Objects.requireNonNull(center);
        this.center = center;
        this.other = new Location(PlacemarkDistanceComparator.class.getSimpleName());
    }

    @Override
    public int compare(Placemark lhs, Placemark rhs) {
        int res = Float.compare(calculateDistance(lhs), calculateDistance(rhs));
        if (res == 0)
            res = Long.compare(lhs.getId(), rhs.getId());
        return res;
    }

    /**
     * Calculate distance to placemark
     * {@see Location@distanceTo}
     */
    public float calculateDistance(final Placemark p) {
        return calculateDistance(p.getLatitude(), p.getLongitude());
    }

    /**
     * Calculate distance to coordinate
     * {@see Location@distanceTo}
     */
    public float calculateDistance(final float latitude, final float longitude) {
        other.setLatitude(latitude);
        other.setLongitude(longitude);
        return center.distanceTo(other);
    }
}
