package io.github.fvasco.pinpoi.model;

import java.io.Serializable;

/**
 * Container for placemark
 *
 * @author Francesco Vasco
 */
public class Placemark implements Serializable {

    private long id;
    private String name;
    private String description;
    private float longitude, latitude;
    private long collectionId;

    public long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(long collectionId) {
        this.collectionId = collectionId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public float getLongitude() {
        return longitude;
    }

    public void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public float getLatitude() {
        return latitude;
    }

    public void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    @Override
    public String toString() {
        return name + '[' + id + "](" + longitude + ',' + latitude + ')';
    }
}
