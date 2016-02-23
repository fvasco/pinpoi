package io.github.fvasco.pinpoi.importer;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.github.fvasco.pinpoi.BuildConfig;
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.ProgressDialogInputStream;
import io.github.fvasco.pinpoi.util.Util;

/**
 * Importer facade for:
 * <ul>
 * <li>KML</li>
 * <li>KMZ</li>
 * <li>GPX</li>
 * <li>RSS</li>
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
    private final BlockingQueue<Placemark> placemarkQueue = new ArrayBlockingQueue<>(256);
    private ProgressDialog progressDialog;
    private String progressDialogMessageFormat;

    public ImporterFacade() {
        this(Util.getApplicationContext());
    }

    public ImporterFacade(Context context) {
        this.placemarkDao = new PlacemarkDao(context);
        this.placemarkCollectionDao = new PlacemarkCollectionDao(context);
    }

    @Nullable
    static AbstractImporter createImporter(@NonNull final String resource) {
        String path;
        try {
            if (Util.isUri(resource)) {
                final List<String> segments = Uri.parse(resource).getPathSegments();
                path = null;
                for (int i = segments.size() - 1; i >= 0 && Util.isEmpty(path); --i) {
                    path = segments.get(i);
                }
            } else {
                path = resource;
            }
        } catch (Exception e) {
            path = resource;
        }
        final AbstractImporter res;
        if (path == null || path.length() < 3) {
            res = null;
        } else {
            final String end = path.substring(path.length() - 3);
            switch (end.toLowerCase()) {
                case "kml":
                    res = new KmlImporter();
                    break;
                case "kmz":
                case "zip":
                    res = new ZipImporter();
                    break;
                case "xml":
                case "rss":
                    res = new GeoRssImporter();
                    break;
                case "gpx":
                    res = new GpxImporter();
                    break;
                case "ov2":
                    res = new Ov2Importer();
                    break;
                case "asc":
                case "csv":
                case "txt":
                    res = new TextImporter();
                    break;
                default:
                    res = null;
            }
        }
        Log.d(ImporterFacade.class.getSimpleName(),
                "Importer for " + resource + " is " + (res == null ? null : res.getClass().getSimpleName()));
        return res;
    }

    /**
     * Set optional progress dialog to show and update with progress
     *
     * @see #setProgressDialogMessageFormat(String)
     */
    public void setProgressDialog(ProgressDialog progressDialog) {
        this.progressDialog = progressDialog;
    }

    public void setProgressDialogMessageFormat(String progressDialogMessageFormat) {
        this.progressDialogMessageFormat = progressDialogMessageFormat;
    }

    /**
     * Import a generic resource into data base, this action refresh collection.
     * If imported count is 0 no modification is done.
     * <b>Side effect</b> update and save placermak collection
     *
     * @return imported {@linkplain io.github.fvasco.pinpoi.model.Placemark}
     */
    public int importPlacemarks(@NonNull final PlacemarkCollection placemarkCollection) throws IOException {
        final String resource = placemarkCollection.getSource();
        Objects.requireNonNull(resource, "Null source");
        placemarkCollectionDao.open();
        try {
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
                    InputStream inputStream;
                    final int max;
                    if (resource.startsWith("/")) {
                        final File file = new File(resource);
                        max = (int) file.length();
                        inputStream = new BufferedInputStream(new FileInputStream(file));
                    } else {
                        final URLConnection urlConnection = new URL(resource).openConnection();
                        inputStream = urlConnection.getInputStream();
                        max = urlConnection.getContentLength();
                    }
                    if (progressDialog != null) {
                        progressDialog.setIndeterminate(max <= 0);
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        if (max > 0) {
                            progressDialog.setMax(max);
                            inputStream = new ProgressDialogInputStream(inputStream, progressDialog);
                        }
                        Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.show();
                            }
                        });
                    }

                    try {
                        importer.importPlacemarks(inputStream);
                    } finally {
                        placemarkQueue.put(STOP_PLACEMARK);
                        inputStream.close();
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
                        if (progressDialog != null && progressDialogMessageFormat != null) {
                            final String message = String.format(progressDialogMessageFormat, placemarkCount);
                            Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.setMessage(message);
                                }
                            });
                        }
                    } catch (IllegalArgumentException e) {
                        // discard (duplicate?) placemark
                        if (BuildConfig.DEBUG) {
                            Log.i(ImporterFacade.class.getSimpleName(), "Placemark discarded " + placemark, e);
                        }
                    }
                }
                if (progressDialog != null) {
                    Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setIndeterminate(true);
                        }
                    });
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
            } catch (InterruptedException | RuntimeException e) {
                throw new IOException("Error importing placemark", e);
            } catch (ExecutionException e) {
                throw new IOException("Error importing placemark", e.getCause());
            } finally {
                importFuture.cancel(true);
                placemarkDaoDatabase.endTransaction();
                placemarkDao.close();
            }
        } finally {
            placemarkCollectionDao.close();
            if (progressDialog != null) {
                Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                    }
                });
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
