package io.github.fvasco.pinpoi.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.PlacemarkDistanceComparator;

/**
 * Dao for {@linkplain io.github.fvasco.pinpoi.model.Placemark}
 *
 * @author Francesco Vasco
 */
public class PlacemarkDao extends AbstractDao {

    private PlacemarkDatabase dbHelper;

    public PlacemarkDao(Context context) {
        dbHelper = new PlacemarkDatabase(context);
        setSqLiteOpenHelper(dbHelper);
    }

    /**
     * Search {@linkplain Placemark} near location
     *
     * @param location      the center of search
     * @param radius        radius of search, in meters
     * @param collectionIds collection id filter
     * @return
     */
    public SortedSet<Placemark> getAllPlacemarkNear(final Location location, final int radius, final long... collectionIds) {
        final SortedSet<Placemark> res = new TreeSet<>(new PlacemarkDistanceComparator(location));

        // calculate "square" of search
        final Location shiftX = new Location(location);
        location.setLongitude(location.getLongitude() + 1);
        final float scaleX = location.distanceTo(shiftX);
        final Location shiftY = new Location(location);
        location.setLatitude(location.getLatitude() + 1);
        final float scaleY = location.distanceTo(shiftY);

        final StringBuilder query = new StringBuilder("WHERE longitude between ? and ? and latitude between ? and ?");
        final List<String> params = new ArrayList<>(
                Arrays.asList(String.valueOf(location.getLongitude() - radius / scaleX),
                        String.valueOf(location.getLongitude() + radius / scaleX),
                        String.valueOf(location.getLatitude() - radius / scaleY),
                        String.valueOf(location.getLatitude() + radius / scaleY))
        );
        if (collectionIds.length > 0) {
            query.append(" AND collection_id in (");
            query.append(collectionIds[0]);
            for (int i = collectionIds.length - 1; i > 0; --i) {
                query.append(',').append(collectionIds[i]);
            }
            query.append(')');
        }
        final Cursor cursor = database.query("PLACEMARK",
                null, "WHERE longitude between ? and ? and latitude between ? and ? and collection_id=?",
                params.toArray(new String[params.size()]), null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            res.add(cursorToPlacemark(cursor));
            cursor.moveToNext();
        }
        cursor.close();
        return res;
    }

    public void insert(Placemark p) {
        database.insert("PLACEMARK", null, placemarkToContentValues(p));
    }

    public void deleteByCollectionId(final long collectionId) {
        database.delete("PLACEMARK", "collection_id=" + collectionId, null);
    }

    ContentValues placemarkToContentValues(final Placemark p) {
        final ContentValues cv = new ContentValues();
        if (p.getId() > 0) {
            cv.put("_ID", p.getId());
        }
        cv.put("longitude", p.getLongitude());
        cv.put("latitude", p.getLatitude());
        cv.put("name", p.getName());
        cv.put("description", p.getDescription());
        cv.put("collection_id", p.getCollectionId());
        return cv;
    }

    Placemark cursorToPlacemark(Cursor cursor) {
        final Placemark p = new Placemark();
        p.setId(cursor.getLong(0));
        p.setLongitude(cursor.getFloat(1));
        p.setLatitude(cursor.getFloat(2));
        p.setName(cursor.getString(3));
        p.setDescription(cursor.getString(4));
        p.setCollectionId(cursor.getLong(5));
        return p;
    }
}
