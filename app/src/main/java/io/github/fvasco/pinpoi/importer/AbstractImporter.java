package io.github.fvasco.pinpoi.importer;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.fvasco.pinpoi.BuildConfig;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Util;

/**
 * Abstract base importer.
 *
 * @author Francesco Vasco
 */
public abstract class AbstractImporter {

    private Consumer<Placemark> consumer;
    private long collectionId;

    public long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(long collectionId) {
        this.collectionId = collectionId;
    }

    public Consumer<Placemark> getConsumer() {
        return consumer;
    }

    public void setConsumer(final Consumer<Placemark> consumer) {
        this.consumer = consumer;
    }

    /**
     * Import data
     *
     * @param inputStream data source
     * @throws IOException error during reading
     */
    public void importPlacemarks(@NonNull final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream);
        if (consumer == null) {
            throw new IllegalStateException("Consumer not defined");
        }
        if (collectionId <= 0) {
            throw new IllegalStateException("Collection id not valid: " + collectionId);
        }
        // do import
        importImpl(inputStream);
    }

    protected void importPlacemark(@NonNull final Placemark placemark) {
        final float latitude = placemark.getLatitude();
        final float longitude = placemark.getLongitude();
        if (!Float.isNaN(latitude) && latitude >= -90F && latitude <= 90F
                && !Float.isNaN(longitude) && longitude >= -180F && longitude <= 180F) {
            final String name = Util.trim(placemark.getName());
            String description = Util.trim(placemark.getDescription());
            if (Util.isEmpty(description) || description.equals(name)) description = null;
            placemark.setName(name);
            placemark.setDescription(description);
            placemark.setCollectionId(collectionId);
            if (BuildConfig.DEBUG) {
                Log.d(AbstractImporter.class.getSimpleName(), "importPlacemark " + placemark);
            }
            consumer.accept(placemark);
        } else if (BuildConfig.DEBUG) {
            Log.d(AbstractImporter.class.getSimpleName(), "importPlacemark skip " + placemark);
        }
    }

    /**
     * Configure importer from another
     */
    protected void configureFrom(final AbstractImporter importer) {
        setCollectionId(importer.getCollectionId());
        setConsumer(importer.getConsumer());
    }

    /**
     * Read datas, use {@linkplain #importPlacemark(Placemark)} to persistence it
     *
     * @param inputStream data source
     * @throws IOException error during reading
     */
    protected abstract void importImpl(@NonNull InputStream inputStream) throws IOException;
}
