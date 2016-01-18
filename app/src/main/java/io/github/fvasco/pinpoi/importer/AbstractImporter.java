package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Consumer;

/**
 * Abstract base importer.
 *
 * @author Francesco Vasco
 */
public abstract class AbstractImporter {

    /**
     * Signal to stop future working
     */
    private static final Placemark STOP_PLACEMARK = new Placemark();
    protected Consumer<Placemark> consumer;
    protected long collectionId;
    private int importedPlacemarkCount = 0;

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
        // do import
        importImpl(inputStream);
        // stop future signal
        return importedPlacemarkCount;
    }

    protected void importPlacemark(final Placemark placemark) {
        assert placemark.getId() == 0;
        assert !placemark.getName().isEmpty();
        assert placemark.getCollectionId() == 0 || placemark.getCollectionId() == collectionId;
        if (placemark.getLongitude() != 0 && placemark.getLatitude() != 0) {
            placemark.setCollectionId(collectionId);
            Log.d("importer", "importPlacemark " + placemark);
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
