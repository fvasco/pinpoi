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
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.util.PlacemarkDistanceComparator;

/**
 * Dao for {@linkplain io.github.fvasco.pinpoi.model.Placemark}
 *
 * @author Francesco Vasco
 */
/*
 * Save coordinate as int: coordinate*100000
 */
public class PlacemarkDao extends AbstractDao {

    // 2^20
    private static float COORDINATE_MULTIPLIER = 1048576F;

    private PlacemarkDatabase dbHelper;

    public PlacemarkDao(Context context) {
        dbHelper = new PlacemarkDatabase(context);
        setSqLiteOpenHelper(dbHelper);
    }

    /**
     * Convert DB coordinate to float
     *
     * @param i db coordinate
     * @return float coordinate
     */
    private static float toFloat(int i) {
        return i / COORDINATE_MULTIPLIER;
    }

    /**
     * Convert float to db coordinate
     *
     * @param f float coordinate
     * @return db coordinate
     */
    private static int toInt(float f) {
        return Math.round(f * COORDINATE_MULTIPLIER);
    }

    private static int toInt(double d) {
        return (int) Math.round(d * COORDINATE_MULTIPLIER);
    }

    public List<Placemark> findAllPlacemarkByCollectionId(final long collectionId) {
        final List<Placemark> res = new ArrayList<>();

        final Cursor cursor = database.query("PLACEMARK", null,
                "collection_id=" + collectionId, null, null, null, null);

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
    public SortedSet<Placemark> findAllPlacemarkNear(final Location location, final int radius, final Collection<Long> collectionIds) {
        Objects.requireNonNull(location, "location not set");
        Objects.requireNonNull(collectionIds, "collection not set");
        if (collectionIds.isEmpty()) {
            throw new IllegalArgumentException("collection empty");
        }

        final SortedSet<Placemark> res = new TreeSet<>(new PlacemarkDistanceComparator(location));

        // calculate "square" of search
        final Location shiftY = new Location(location);
        shiftY.setLatitude(location.getLatitude() + 1);
        final float scaleY = location.distanceTo(shiftY);
        final Location shiftX = new Location(location);
        // calculate scale on /smaller/ border -> larger area
        shiftX.setLatitude(Math.abs(location.getLatitude()) + radius / scaleY);
        shiftX.setLongitude(location.getLongitude() + 1);
        final float scaleX = location.distanceTo(shiftX);

        // where clause
        // collection ids
        final StringBuilder where = new StringBuilder("collection_id in (");
        final Iterator<Long> iterator = collectionIds.iterator();
        where.append(iterator.next().toString());
        while (iterator.hasNext()) {
            where.append(',').append(iterator.next().toString());
        }
        // longitude, latitude
        where.append(") AND latitude between ? and ? AND longitude between ? and ?");
        final List<String> params =
                Arrays.asList(
                        String.valueOf(toInt(location.getLatitude() - radius / scaleY)),
                        String.valueOf(toInt(location.getLatitude() + radius / scaleY)),
                        String.valueOf(toInt(location.getLongitude() - radius / scaleX)),
                        String.valueOf(toInt(location.getLongitude() + radius / scaleX)));


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

    /**
     * Get annotation for a placemark
     *
     * @param placemark
     * @return annotaion for a placemark
     */
    public PlacemarkAnnotation loadPlacemarkAnnotation(Placemark placemark) {
        final Cursor cursor = database.query("PLACEMARK_ANNOTATION", null,
                "latitude=" + toInt(placemark.getLatitude()) + " AND longitude=" + toInt(placemark.getLongitude()), null,
                null, null, null);

        PlacemarkAnnotation res = null;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            res = cursorToPlacemarkAnnotation(cursor);
            cursor.moveToNext();
        }
        cursor.close();

        if (res == null) {
            res = new PlacemarkAnnotation();
            res.setLatitude(placemark.getLatitude());
            res.setLongitude(placemark.getLongitude());
            res.setNote("");
        }
        return res;
    }

    public void update(final PlacemarkAnnotation placemarkAnnotation) {
        if (placemarkAnnotation.getNote().isEmpty() && !placemarkAnnotation.isFlagged()) {
            database.delete("PLACEMARK_ANNOTATION", "_ID=" + placemarkAnnotation.getId(), null);
            placemarkAnnotation.setId(0);
        } else {
            if (placemarkAnnotation.getId() > 0) {
                final int count = database.update("PLACEMARK_ANNOTATION", placemarkAnnotationToContentValues(placemarkAnnotation), "_ID=" + placemarkAnnotation.getId(), null);
                if (count == 0) {
                    placemarkAnnotation.setId(0);
                }
            }
            if (placemarkAnnotation.getId() == 0) {
                final long id = database.insert("PLACEMARK_ANNOTATION", null, placemarkAnnotationToContentValues(placemarkAnnotation));
                if (id == -1) {
                    throw new IllegalArgumentException("Data not valid");
                }
                assert id > 0;
                placemarkAnnotation.setId(id);
            }
        }
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
        cv.put("latitude", toInt(p.getLatitude()));
        cv.put("longitude", toInt(p.getLongitude()));
        cv.put("name", p.getName());
        cv.put("description", p.getDescription());
        cv.put("collection_id", p.getCollectionId());
        return cv;
    }

    ContentValues placemarkAnnotationToContentValues(final PlacemarkAnnotation pa) {
        final ContentValues cv = new ContentValues();
        cv.put("latitude", toInt(pa.getLatitude()));
        cv.put("longitude", toInt(pa.getLongitude()));
        cv.put("note", pa.getNote());
        cv.put("flag", pa.isFlagged() ? 1 : 0);
        return cv;
    }

    Placemark cursorToPlacemark(Cursor cursor) {
        final Placemark p = new Placemark();
        p.setId(cursor.getLong(0));
        p.setLatitude(toFloat(cursor.getInt(1)));
        p.setLongitude(toFloat(cursor.getInt(2)));
        p.setName(cursor.getString(3));
        p.setDescription(cursor.getString(4));
        p.setCollectionId(cursor.getLong(5));
        return p;
    }

    PlacemarkAnnotation cursorToPlacemarkAnnotation(Cursor cursor) {
        final PlacemarkAnnotation pa = new PlacemarkAnnotation();
        pa.setId(cursor.getLong(0));
        pa.setLatitude(toFloat(cursor.getInt(1)));
        pa.setLongitude(toFloat(cursor.getInt(2)));
        pa.setNote(cursor.getString(3));
        pa.setFlagged(cursor.getInt(4) != 0);
        return pa;
    }
}
