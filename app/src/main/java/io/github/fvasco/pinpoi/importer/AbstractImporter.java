package io.github.fvasco.pinpoi.importer;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Util;

/**
 * Abstract base importer.
 *
 * @author Francesco Vasco
 */
public abstract class AbstractImporter {

    protected Consumer<Placemark> consumer;
    protected long collectionId;
    private int importedPlacemarkCount = 0;

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
    public int importPlacemarks(@NonNull InputStream inputStream) throws IOException {
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
        return importedPlacemarkCount;
    }

    protected void importPlacemark(@NonNull final Placemark placemark) {
        float latitude = placemark.getLatitude();
        float longitude = placemark.getLongitude();
        if (latitude >= -90F && latitude <= 90F
                && longitude >= -180F && longitude <= 180F) {
            placemark.setName(Util.trim(placemark.getName()));
            placemark.setDescription(Util.trim(placemark.getDescription()));
            placemark.setCollectionId(collectionId);
            Log.d(AbstractImporter.class.getSimpleName(), "importPlacemark " + placemark);
            consumer.accept(placemark);
            ++importedPlacemarkCount;
        } else {
            Log.d(AbstractImporter.class.getSimpleName(), "importPlacemark skip " + placemark);
        }
    }

    /**
     * Read datas, use {@linkplain #importPlacemark(Placemark)} to persistence it
     *
     * @param inputStream data source
     * @throws IOException error during reading
     */
    protected abstract void importImpl(@NonNull InputStream inputStream) throws IOException;

}
