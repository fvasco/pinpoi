package io.github.fvasco.pinpoi.util;

import android.location.Location;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Simple coordinates
 *
 * @author Francesco Vasco
 */
public class Coordinates implements Cloneable {
    public final float latitude;
    public final float longitude;

    public Coordinates(final float latitude, final float longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Coordinates fromPlacemark(Placemark placemark) {
        return new Coordinates(placemark.getLatitude(), placemark.getLongitude());
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Coordinates that = (Coordinates) o;

        return Float.compare(that.latitude, latitude) == 0
                && Float.compare(that.longitude, longitude) == 0;

    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(latitude) ^ Integer.rotateLeft(Float.floatToIntBits(longitude), 16);
    }

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public Coordinates withLatitude(final float newLatitude) {
        return new Coordinates(newLatitude, longitude);
    }

    public Coordinates withLongitude(final float newLongitude) {
        return new Coordinates(latitude, newLongitude);
    }

    public float distanceTo(Coordinates other) {
        final float[] result = new float[1];
        Location.distanceBetween(latitude, longitude, other.latitude, other.longitude, result);
        return result[0];
    }

    @Override
    public String toString() {
        return Location.convert(latitude, Location.FORMAT_DEGREES)
                + ',' + Location.convert(longitude, Location.FORMAT_DEGREES);
    }
}
