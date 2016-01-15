package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.ThreadUtil;

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
    private final BlockingQueue<Placemark> placemarkQueue = new ArrayBlockingQueue<Placemark>(64);
    protected Consumer<Placemark> consumer;
    protected long collectionId;
    private Future<Integer> consumerFuture;

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
        consumerFuture = ThreadUtil.EXECUTOR.submit(new ConsumerCallable());
        try {
            // do import
            importImpl(inputStream);
            // stop future signal
            placemarkQueue.put(STOP_PLACEMARK);
            return consumerFuture.get();
        } catch (InterruptedException e) {
            Log.d("importer", "importPlacemarks exception", e);
            throw new IOException(e);
        } catch (ExecutionException e) {
            Log.d("importer", "importPlacemarks exception", e);
            throw new IOException(e.getCause());
        } finally {
            consumerFuture.cancel(true);
            consumerFuture = null;
            placemarkQueue.clear();
        }
    }

    protected void importPlacemark(final Placemark placemark) {
        assert placemark.getId() == 0;
        assert !placemark.getName().isEmpty();
        assert placemark.getCollectionId() == 0 || placemark.getCollectionId() == collectionId;
        try {
            if (placemark.getLongitude() != 0 && placemark.getLatitude() != 0) {
                placemark.setCollectionId(collectionId);
                Log.d("importer", "importPlacemark " + placemark);
                placemarkQueue.put(placemark);
            } else {
                Log.d("importer", "importPlacemark skip " + placemark);
            }
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Read datas, use {@linkplain #importPlacemark(Placemark)} to persistence it
     *
     * @param inputStream data source
     * @throws IOException error during reading
     */
    protected abstract void importImpl(InputStream inputStream) throws IOException;

    /**
     * Call consumer and return imported {@linkplain Placemark}
     */
    private final class ConsumerCallable implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            int importedPlacemarkCount = 0;
            Placemark placemark;
            while ((placemark = placemarkQueue.take()) != STOP_PLACEMARK) {
                consumer.accept(placemark);
                ++importedPlacemarkCount;
            }
            return importedPlacemarkCount;
        }
    }

}
