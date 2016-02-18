package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import io.github.fvasco.pinpoi.BuildConfig;
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * Debug utilities
 *
 * @author Francesco Vasco
 */
public final class DebugUtil {
    private DebugUtil() {
    }

    public static void setUpDebugDatabase(final Context context) {
        if (!BuildConfig.DEBUG) throw new Error();

        try (final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(context).open();
             final PlacemarkDao placemarkDao = new PlacemarkDao(context).open()) {
            final SQLiteDatabase placemarkCollectionDatabase = placemarkCollectionDao.getDatabase();
            final SQLiteDatabase placemarkDatabase = placemarkDao.getDatabase();
            placemarkCollectionDatabase.beginTransaction();
            placemarkDatabase.beginTransaction();
            try {
                // clear all database
                for (final PlacemarkCollection placemarkCollection : placemarkCollectionDao.findAllPlacemarkCollection()) {
                    placemarkDao.deleteByCollectionId(placemarkCollection.getId());
                    placemarkCollectionDao.delete(placemarkCollection);
                }

                // recreate test database
                final PlacemarkCollection placemarkCollection = new PlacemarkCollection();
                for (int pci = 15; pci >= 0; --pci) {
                    placemarkCollection.setId(0);
                    placemarkCollection.setName("Placemark Collection " + pci);
                    placemarkCollection.setCategory(pci == 0 ? null : "Category " + (pci % 7));
                    placemarkCollection.setDescription(placemarkCollection.getName() + " long long long description");
                    placemarkCollection.setSource("http://source/" + pci + ".csv");
                    placemarkCollection.setPoiCount(pci);
                    placemarkCollection.setLastUpdate(pci * 10_000_000);
                    placemarkCollectionDao.insert(placemarkCollection);
                    Log.i(DebugUtil.class.getSimpleName(), "inserted " + placemarkCollection);

                    final Placemark placemark = new Placemark();
                    for (int lat = -60; lat < 60; ++lat) {
                        for (int lon = -90; lon < 90; lon += 2) {
                            placemark.setId(0);
                            placemark.setName("Placemark " + lat + "," + lon + "/" + pci);
                            placemark.setDescription((lat + lon) % 10 == 0 ? null : placemark.getName() + " description");
                            placemark.setLatitude((float) (lat + Math.sin(lat + pci)));
                            placemark.setLongitude((float) (lon + Math.sin(lon - pci)));
                            placemark.setCollectionId(placemarkCollection.getId());
                            placemarkDao.insert(placemark);

                            if ((lat + lon + pci) % 9 == 0) {
                                final PlacemarkAnnotation placemarkAnnotation = placemarkDao.loadPlacemarkAnnotation(placemark);
                                placemarkAnnotation.setFlagged((lat + lon + pci) % 3 == 0);
                                placemarkAnnotation.setNote("Placemark annotation for " + placemark.getName());
                                placemarkDao.update(placemarkAnnotation);
                            }
                        }
                    }
                }

                placemarkDatabase.setTransactionSuccessful();
                placemarkCollectionDatabase.setTransactionSuccessful();
            } finally {
                placemarkDatabase.endTransaction();
                placemarkCollectionDatabase.endTransaction();
            }
        }
    }


}
