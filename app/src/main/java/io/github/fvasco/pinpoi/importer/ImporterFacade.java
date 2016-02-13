package io.github.fvasco.pinpoi.importer;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

    public ImporterFacade() {
        this(Util.getApplicationContext());
    }

    public ImporterFacade(Context context) {
        this.placemarkDao = new PlacemarkDao(context);
        this.placemarkCollectionDao = new PlacemarkCollectionDao(context);
    }

    @Nullable
    static AbstractImporter createImporter(@NonNull String resource) {
        if (resource.startsWith("content://")) {
            final Uri resourceUri = Uri.parse(resource);
            final String[] mimeTypes = Util.getApplicationContext().getContentResolver().getStreamTypes(resourceUri, "*/*");
            if (mimeTypes != null) {
                for (final String mimeType : mimeTypes)
                    switch (mimeType) {
                        case "application/vnd.google-earth.kml+xml":
                            return new KmlImporter();
                        case "application/zip":
                        case "application/vnd.google-earth.kmz":
                            return new ZipImporter();
                        case "application/gpx":
                        case "application/gpx+xml":
                            return new GpxImporter();
                        case "application/csv":
                        case "text/csv":
                        case "text/plain":
                            return new TextImporter();
                    }
            }
            return createImporter(resourceUri.getLastPathSegment());
        } else {
            String path;
            try {
                path = Util.isUri(resource)
                        ? Uri.parse(resource).getLastPathSegment()
                        : resource;
            } catch (Exception e) {
                path = resource;
            }
            path = path.toLowerCase();
            if (path.endsWith(".kml")) {
                return new KmlImporter();
            } else if (path.endsWith(".zip") || path.endsWith(".kmz")) {
                return new ZipImporter();
            } else if (path.endsWith(".gpx")) {
                return new GpxImporter();
            } else if (path.endsWith(".ov2")) {
                return new Ov2Importer();
            } else if (path.endsWith(".asc") || path.endsWith(".csv") || path.endsWith(".txt")) {
                return new TextImporter();
            }
        }
        return null;
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
     * <b>Side effect</b> update and save placermak collection
     *
     * @return imported {@linkplain io.github.fvasco.pinpoi.model.Placemark}
     */
    public int importPlacemarks(@NonNull final PlacemarkCollection placemarkCollection) throws IOException {
        placemarkCollectionDao.open();
        try {
            if (progressDialog != null) {
                progressDialog.setMax(placemarkCollection.getPoiCount());
                progressDialog.setIndeterminate(placemarkCollection.getPoiCount() <= 0);
            }

            final String resource = placemarkCollection.getSource();
            final AbstractImporter importer = createImporter(resource);
            if (importer == null) {
                throw new IOException("Cannot import " + resource);
            }
            importer.setCollectionId(placemarkCollection.getId());
            importer.setConsumer(this);

            // insert new placemark
            final Future<Void> importFuture = Util.EXECUTOR.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try (final InputStream inputStream = new BufferedInputStream(
                            resource.startsWith("file:///") ? new FileInputStream(resource.substring(7))
                                    : resource.startsWith("file:/") ? new FileInputStream(resource.substring(5))
                                    : resource.startsWith("/") ? new FileInputStream(resource)
                                    : resource.startsWith("content:/") ? Util.getApplicationContext().getContentResolver().openInputStream(Uri.parse(resource))
                                    : new URL(resource).openStream())) {
                        importer.importPlacemarks(inputStream);
                    } finally {
                        placemarkQueue.put(STOP_PLACEMARK);
                    }
                    return null;
                }
            });
            int placemarkCount = 0;
            placemarkDao.open();
            SQLiteDatabase placemarkDaoDatabase = placemarkDao.getDatabase();
            placemarkDaoDatabase.beginTransaction();
            try {
                // remove old placemark
                placemarkDao.deleteByCollectionId(placemarkCollection.getId());
                Placemark placemark;
                while ((placemark = placemarkQueue.take()) != STOP_PLACEMARK) {
                    try {
                        placemarkDao.insert(placemark);
                        ++placemarkCount;
                        if (progressDialog != null) {
                            progressDialog.setProgress(placemarkCount);
                            if (placemarkCount == progressDialog.getMax()) {
                                progressDialog.setIndeterminate(true);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // discard (duplicate?) placemark
                        Log.i(ImporterFacade.class.getSimpleName(), "Placemark discarded " + placemark, e);
                    }
                }
                // wait import and check exception
                importFuture.get();
                if (placemarkCount > 0) {
                    // update placemark collection
                    placemarkCollection.setLastUpdate(System.currentTimeMillis());
                    placemarkCollection.setPoiCount(placemarkCount);
                    placemarkCollectionDao.update(placemarkCollection);
                    // confirm transaction
                    placemarkDaoDatabase.setTransactionSuccessful();
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
                placemarkDaoDatabase.endTransaction();
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
        } catch (InterruptedException e) {
            Log.w(ImporterFacade.class.getSimpleName(), "Placemark discarded " + p, e);
            throw new RuntimeException(e);
        }
    }
}
