package io.github.fvasco.pinpoi.importer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.ApplicationContextHolder;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.ThreadUtil;

/**
 * Importer facade for:
 * <ul>
 * <li>KML</li>
 * <li>KMZ</li>
 * <li>GPX</li>
 * <li>OV2 Tomtom</li>
 * <li>ASC, CSV files</li>
 * </ul>
 * <p/>
 * Import {@linkplain Placemark} and update {@linkplain PlacemarkCollectionDao}
 *
 * @author Francesco Vasco
 */
public class ImporterFacade implements Consumer<Placemark> {

    /**
     * Signpost for end of elaboration
     */
    private final Placemark END_PLACEMARK = new Placemark();
    private final PlacemarkDao placemarkDao;
    private final PlacemarkCollectionDao placemarkCollectionDao;
    private final BlockingQueue<Placemark> placemarkQueue = new ArrayBlockingQueue<Placemark>(64);

    public ImporterFacade() {
        this(ApplicationContextHolder.get());
    }

    public ImporterFacade(Context context) {
        this.placemarkDao = new PlacemarkDao(context);
        this.placemarkCollectionDao = new PlacemarkCollectionDao(context);
    }

    /**
     * Import a generic resource into data base, this action refresh collection.
     * If imported count is 0 no modification is done.
     *
     * @return imported {@linkplain io.github.fvasco.pinpoi.model.Placemark}
     */
    public int importPlacemarks(final long collectionId) throws IOException {
        placemarkCollectionDao.open();
        try {
            final PlacemarkCollection placemarkCollection = placemarkCollectionDao.findPlacemarkCollectionById(collectionId);
            if (placemarkCollection == null) {
                throw new IllegalArgumentException("Placemark collection " + collectionId + " not found");
            }
            final String resource = placemarkCollection.getSource();
            final AbstractImporter importer;
            if (resource.endsWith("kml")) {
                importer = new KmlImporter();
            } else if (resource.endsWith("kmz")) {
                importer = new KmzImporter();
            } else if (resource.endsWith("gpx")) {
                importer = new GpxImporter();
            } else if (resource.endsWith("ov2")) {
                importer = new Ov2Importer();
            } else {
                importer = new TextImporter();
            }

            importer.setCollectionId(collectionId);
            importer.setConsumer(this);

            // insert new placemark
            final Future<Integer> importFuture = ThreadUtil.EXECUTOR.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    try (final InputStream inputStream = new BufferedInputStream(resource.startsWith("/")
                            ? new FileInputStream(resource)
                            : new URL(resource).openStream())) {
                        return importer.importPlacemarks(inputStream);
                    } finally {
                        placemarkQueue.put(END_PLACEMARK);
                    }
                }
            });
            placemarkDao.open();
            SQLiteDatabase daoDatabase = placemarkDao.getDatabase();
            daoDatabase.beginTransaction();
            try {
                // remove old placemark
                placemarkDao.deleteByCollectionId(collectionId);
                Placemark placemark;
                while ((placemark = placemarkQueue.take()) != END_PLACEMARK) {
                    placemarkDao.insert(placemark);
                }
                final int count = importFuture.get();
                // confirm transaction
                if (count > 0) {
                    daoDatabase.setTransactionSuccessful();
                    // update placemark collection
                    placemarkCollection.setLastUpdate(new Date());
                    placemarkCollection.setPoiCount(count);
                    placemarkCollectionDao.update(placemarkCollection);
                }
                return count;
            } catch (InterruptedException e) {
                throw new RuntimeException("Error importing placemark", e);
            } catch (ExecutionException e) {
                final Throwable target = e.getCause();
                if (target instanceof RuntimeException) {
                    throw (RuntimeException) target;
                } else if (target instanceof IOException) {
                    throw (IOException) target;
                } else {
                    throw new RuntimeException("Error importing placemark", e);
                }
            } finally {
                importFuture.cancel(true);
                daoDatabase.endTransaction();
                placemarkDao.close();
            }
        } finally {
            placemarkCollectionDao.close();
        }
    }

    @Override
    public void accept(Placemark p) {
        try {
            placemarkQueue.put(p);
        } catch (InterruptedException e) {
            Log.w("importer", "Placemark discarded " + p, e);
            throw new RuntimeException(e);
        }
    }
}
