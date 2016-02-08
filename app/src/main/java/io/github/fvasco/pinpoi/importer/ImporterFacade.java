package io.github.fvasco.pinpoi.importer;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Util;

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
    private final Placemark STOP_PLACEMARK = new Placemark();
    private final PlacemarkDao placemarkDao;
    private final PlacemarkCollectionDao placemarkCollectionDao;
    private final BlockingQueue<Placemark> placemarkQueue = new ArrayBlockingQueue<>(64);
    private ProgressDialog progressDialog;
    private int placemarkCount;

    public ImporterFacade() {
        this(Util.getApplicationContext());
    }

    public ImporterFacade(Context context) {
        this.placemarkDao = new PlacemarkDao(context);
        this.placemarkCollectionDao = new PlacemarkCollectionDao(context);
    }

    /**
     * Set progress dialog to show and update
     */
    public void setProgressDialog(ProgressDialog progressDialog) {
        this.progressDialog = progressDialog;
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

            if (progressDialog != null) {
                progressDialog.setMax(placemarkCollection.getPoiCount());
                progressDialog.setIndeterminate(placemarkCollection.getPoiCount() <= 0);
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
            final Future<Integer> importFuture = Util.EXECUTOR.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    try (final InputStream inputStream = new BufferedInputStream(resource.startsWith("/")
                            ? new FileInputStream(resource)
                            : new URL(resource).openStream())) {
                        return importer.importPlacemarks(inputStream);
                    } finally {
                        placemarkQueue.put(STOP_PLACEMARK);
                    }
                }
            });
            placemarkCount = 0;
            placemarkDao.open();
            SQLiteDatabase daoDatabase = placemarkDao.getDatabase();
            daoDatabase.beginTransaction();
            try {
                // remove old placemark
                placemarkDao.deleteByCollectionId(collectionId);
                Placemark placemark;
                while ((placemark = placemarkQueue.take()) != STOP_PLACEMARK) {
                    placemarkDao.insert(placemark);
                }
                placemarkCount = importFuture.get();
                // confirm transaction
                if (placemarkCount > 0) {
                    daoDatabase.setTransactionSuccessful();
                    // update placemark collection
                    placemarkCollection.setLastUpdate(System.currentTimeMillis());
                    placemarkCollection.setPoiCount(placemarkCount);
                    placemarkCollectionDao.update(placemarkCollection);
                }
                return placemarkCount;
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
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        }
    }

    @Override
    public void accept(Placemark p) {
        try {
            placemarkQueue.put(p);
            ++placemarkCount;
            if (progressDialog != null) {
                progressDialog.setProgress(placemarkCount);
                if (placemarkCount == progressDialog.getMax()) {
                    progressDialog.setIndeterminate(true);
                }
            }
        } catch (InterruptedException e) {
            Log.w(ImporterFacade.class.getSimpleName(), "Placemark discarded " + p, e);
            throw new RuntimeException(e);
        }
    }
}
