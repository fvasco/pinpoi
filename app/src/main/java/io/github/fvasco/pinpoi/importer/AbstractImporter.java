package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Consumer;

/**
 * Abstract base importer. Class is not synchronized.
 *
 * @author Francesco Vasco
 */
public abstract class AbstractImporter {

    protected int importedPlacemarkCount;
    protected Consumer<Placemark> consumer;
    protected long collectionId;

    public long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(long collectionId) {
        this.collectionId = collectionId;
    }

    public Consumer<Placemark> getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer<Placemark> consumer) {
        this.consumer = consumer;
    }

    /**
     * Import data
     *
     * @param inputStream data source
     * @return imported POI
     * @throws IOException error during reading
     */
    public int importPlacemarks(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream);
        if (consumer == null) {
            throw new IllegalStateException("Consumer not defined");
        }
        if (collectionId <= 0) {
            throw new IllegalStateException("Collection id not defined");
        }
        importedPlacemarkCount = 0;
        importImpl(inputStream);
        return importedPlacemarkCount;
    }

    protected void importPlacemark(final Placemark placemark) {
        if (placemark.getLongitude() != 0 && placemark.getLatitude() != 0) {
            Log.d("importer", "importPlacemark " + placemark);
            placemark.setCollectionId(collectionId);
            consumer.accept(placemark);
            ++importedPlacemarkCount;
        } else {
            Log.d("importer", "importPlacemark skip " + placemark);
        }
    }

    /**
     * Read datas, use {@linkplain #importPlacemark(Placemark)} to persistence it
     *
     * @param inputStream data source
     * @throws IOException error during reading
     */
    protected abstract void importImpl(InputStream inputStream) throws IOException;

}
