package io.github.fvasco.pinpoi.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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

    public List<Placemark> getAllPlacemarkByCollectionId(final long collectionId) {
        final List<Placemark> res = new ArrayList<>();

        final Cursor cursor = database.query("PLACEMARK", null,
                "collecton_id=" + collectionId, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            res.add(cursorToPlacemark(cursor));
            cursor.moveToNext();
        }
        cursor.close();
        return res;
    }

    /**
     * Search {@linkplain Placemark} near location
     *
     * @param location      the center of search
     * @param radius        radius of search, in meters
     * @param collectionIds collection id filter
     * @return
     */
    public SortedSet<Placemark> getAllPlacemarkNear(final Location location, final int radius, final Collection<Long> collectionIds) {
        Objects.requireNonNull(location, "location not set");
        Objects.requireNonNull(collectionIds, "collection not set");
        if (collectionIds.isEmpty()) {
            throw new IllegalArgumentException("collection empty");
        }

        final SortedSet<Placemark> res = new TreeSet<>(new PlacemarkDistanceComparator(location));

        // calculate "square" of search
        final Location shiftX = new Location(location);
        location.setLongitude(location.getLongitude() + 1);
        final float scaleX = location.distanceTo(shiftX);
        final Location shiftY = new Location(location);
        location.setLatitude(location.getLatitude() + 1);
        final float scaleY = location.distanceTo(shiftY);

        final StringBuilder where = new StringBuilder("longitude between ? and ? and latitude between ? and ?");
        final List<String> params = new ArrayList<>(
                Arrays.asList(String.valueOf(location.getLongitude() - radius / scaleX),
                        String.valueOf(location.getLongitude() + radius / scaleX),
                        String.valueOf(location.getLatitude() - radius / scaleY),
                        String.valueOf(location.getLatitude() + radius / scaleY))
        );

        // collection ids
        where.append(" AND collection_id in (");
        final Iterator<Long> iterator = collectionIds.iterator();
        where.append(iterator.next().toString());
        while (iterator.hasNext()) {
            where.append(',').append(iterator.next().toString());
        }
        where.append(')');

        final Cursor cursor = database.query("PLACEMARK", null,
                where.toString(), params.toArray(new String[params.size()]),
                null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            res.add(cursorToPlacemark(cursor));
            cursor.moveToNext();
        }
        cursor.close();
        return res;
    }

    public void insert(Placemark p) {
        final long id = database.insert("PLACEMARK", null, placemarkToContentValues(p));
        if (id == -1) {
            throw new IllegalArgumentException("Data not valid");
        }
        p.setId(id);
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
