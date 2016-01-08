package io.github.fvasco.pinpoi.importer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Consumer;

/**
 * Importer facade
 *
 * @author Francesco Vasco
 */
public class ImporterFacade {

    final PlacemarkDao placemarkDao;

    public ImporterFacade(Context context) {
        this.placemarkDao = new PlacemarkDao(context);
    }

    /**
     * Import a generic resource into data base
     *
     * @param resource resource as absolute file path or URL
     * @return imported {@linkplain io.github.fvasco.pinpoi.model.Placemark}
     */
    public int importPlacemarks(final String resource, final long collectionId) throws IOException {
        final AbstractImporter importer;
        if (resource.endsWith(".kml")) {
            importer = new KmlImporter();
        } else if (resource.endsWith(".kmz")) {
            importer = new KmzImporter();
        } else {
            importer = new TextImporter();
        }

        importer.setCollectionId(collectionId);
        importer.setConsumer(new Consumer<Placemark>() {
            @Override
            public void accept(Placemark p) {
                placemarkDao.insert(p);
            }
        });

        placemarkDao.open();
        SQLiteDatabase database = placemarkDao.getDatabase();
        database.beginTransaction();
        try (final InputStream inputStream = resource.startsWith("/") ? new FileInputStream(resource) :
                new URL(resource).openStream()) {
            // remove old placemark
            placemarkDao.deleteByCollectionId(collectionId);
            // insert new placemark
            int count = importer.importPlacemarks(inputStream);
            // confirm transaction
            if (count > 0) {
                database.setTransactionSuccessful();
            }
            return count;
        } finally {
            database.endTransaction();
            placemarkDao.close();
        }
    }
}
