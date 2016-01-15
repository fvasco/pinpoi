package io.github.fvasco.pinpoi.model;

import java.io.Serializable;

/**
 * A user annotation on {@linkplain Placemark}
 *
 * @author Francesco Vasco
 */
public class PlacemarkAnnotation implements Serializable {
    private long id;
    private float latitude;
    private float longitude;
    private String note;
    private boolean flagged;

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public float getLatitude() {
        return latitude;
    }

    public void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return note + '[' + id + "](" + latitude + ',' + longitude + ')';
    }

}
