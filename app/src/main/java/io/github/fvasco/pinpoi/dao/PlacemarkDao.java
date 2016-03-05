package io.github.fvasco.pinpoi.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.model.PlacemarkBase;
import io.github.fvasco.pinpoi.model.PlacemarkSearchResult;
import io.github.fvasco.pinpoi.util.Coordinates;
import io.github.fvasco.pinpoi.util.DistanceComparator;
import io.github.fvasco.pinpoi.util.Util;

/**
 * Dao for {@linkplain io.github.fvasco.pinpoi.model.Placemark}
 *
 * @author Francesco Vasco
 */
/*
 * Save coordinate as int: coordinate*{@linkplain #COORDINATE_MULTIPLIER}
 */
public class PlacemarkDao extends AbstractDao<PlacemarkDao> {

    /**
     * Max result for {@linkplain #findAllPlacemarkNear(Coordinates, double, String, boolean, Collection)}
     */
    private static final int MAX_NEAR_RESULT = 250;
    private static final boolean SQL_INSTR_PRESENT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

    // 2^20
    private static final float COORDINATE_MULTIPLIER = 1048576F;
    private static PlacemarkDao INSTANCE;

    PlacemarkDao() {
        this(Util.getApplicationContext());
    }

    public PlacemarkDao(@NonNull Context context) {
        super(context);
    }

    public static synchronized PlacemarkDao getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlacemarkDao();
        }
        return INSTANCE;
    }

    /**
     * Convert DB coordinates to double
     *
     * @param i db coordinate
     * @return float coordinates
     */
    private static float coordinateToFloat(int i) {
        return i / COORDINATE_MULTIPLIER;
    }

    /**
     * Convert double to db coordinates
     *
     * @param f float coordinate
     * @return db coordinates
     */
    private static int coordinateToInt(float f) {
        return Math.round(f * COORDINATE_MULTIPLIER);
    }

    private static int coordinateToInt(double f) {
        return (int) Math.round(f * COORDINATE_MULTIPLIER);
    }

    /**
     * Append coordinates filter in stringBuilder sql clause
     */
    private static void createWhereFilter(Coordinates coordinates, double range, final String table, StringBuilder stringBuilder) {
        // calculate "square" of search
        final Coordinates shiftY = coordinates.withLatitude(coordinates.getLatitude() + (coordinates.getLatitude() > 0 ? -1 : 1));
        final float scaleY = coordinates.distanceTo(shiftY);
        final Coordinates shiftX = coordinates.withLongitude(coordinates.getLongitude() + (coordinates.getLongitude() > 0 ? -1 : 1));
        final float scaleX = coordinates.distanceTo(shiftX);

        // latitude
        stringBuilder.append(table).append(".latitude between ")
                .append(String.valueOf(coordinateToInt(coordinates.getLatitude() - range / scaleY))).append(" AND ")
                .append(String.valueOf(coordinateToInt(coordinates.getLatitude() + range / scaleY)));

        // longitude
        final double longitudeMin = coordinates.getLongitude() - range / scaleX;
        final double longitudeMax = coordinates.getLongitude() + range / scaleX;
        stringBuilder.append(" AND (").append(table).append(".longitude between ")
                .append(String.valueOf(coordinateToInt(longitudeMin))).append(" AND ")
                .append(String.valueOf(coordinateToInt(longitudeMax)));
        // fix for meridian 180
        if (longitudeMin < -180.0) {
            stringBuilder.append(" OR ").append(table).append(".longitude >=").append(String.valueOf(coordinateToInt(longitudeMin + 360.0)));
        } else if (longitudeMax > 180.0) {
            stringBuilder.append(" OR ").append(table).append(".longitude <=").append(String.valueOf(coordinateToInt(longitudeMax - 360.0)));
        }
        stringBuilder.append(')');
    }

    @Override
    protected SQLiteOpenHelper createSqLiteOpenHelper(@NonNull Context context) {
        return new PlacemarkDatabase(context);
    }

    public List<Placemark> findAllPlacemarkByCollectionId(final long collectionId) {
        final Cursor cursor = database.query("PLACEMARK", null,
                "collection_id=" + collectionId, null, null, null, "_ID");
        try {
            final List<Placemark> res = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                res.add(cursorToPlacemark(cursor));
                cursor.moveToNext();
            }
            return res;
        } finally {
            cursor.close();
        }
    }

    public SortedSet<PlacemarkSearchResult> findAllPlacemarkNear
            (final Coordinates coordinates,
             final double range,
             final Collection<Long> collectionIds) {
        return findAllPlacemarkNear(coordinates, range, null, false, collectionIds);
    }

    /**
     * Search {@linkplain Placemark} near location
     *
     * @param coordinates   the center of search
     * @param range         radius of search, in meters
     * @param collectionIds collection id filter
     */
    public SortedSet<PlacemarkSearchResult> findAllPlacemarkNear(
            final Coordinates coordinates,
            final double range,
            String nameFilter,
            final boolean onlyFavourite,
            final Collection<Long> collectionIds) {
        Util.requireNonNull(coordinates, "coordinates not set");
        Util.requireNonNull(collectionIds, "collection not set");
        if (collectionIds.isEmpty()) {
            throw new IllegalArgumentException("collection empty");
        }
        if (range <= 0) {
            throw new IllegalArgumentException("range not valid " + range);
        }
        // nameFilter null or UPPERCASE
        if (Util.isEmpty(nameFilter)) {
            nameFilter = null;
        } else {
            nameFilter = nameFilter.trim().toUpperCase();
        }

        // sql clause
        // collection ids
        final StringBuilder sql = new StringBuilder(
                "SELECT p._ID,p.latitude,p.longitude,p.name,pa.flag FROM PLACEMARK p")
                .append(" LEFT OUTER JOIN PLACEMARK_ANNOTATION pa USING(latitude,longitude)");
        sql.append(" WHERE p.collection_id in (");
        final List<String> whereArgs = new ArrayList<>();
        final Iterator<Long> iterator = collectionIds.iterator();
        sql.append(iterator.next().toString());
        while (iterator.hasNext()) {
            sql.append(',').append(iterator.next().toString());
        }
        sql.append(") AND ");
        createWhereFilter(coordinates, range, "p", sql);

        if (onlyFavourite) {
            sql.append(" AND pa.flag=1");
        }

        if (SQL_INSTR_PRESENT && nameFilter != null) {
            sql.append(" AND instr(upper(name),?)>0");
            whereArgs.add(nameFilter);
        }

        final DistanceComparator locationComparator = new DistanceComparator(coordinates);
        final SortedSet<PlacemarkSearchResult> res = new TreeSet<>(locationComparator);
        final Cursor cursor = database.rawQuery(sql.toString(), whereArgs.toArray(new String[whereArgs.size()]));
        try {
            cursor.moveToFirst();
            double maxDistance = range;
            while (!cursor.isAfterLast()) {
                final PlacemarkSearchResult p = cursorToPlacemarkSearchResult(cursor);
                if (locationComparator.calculateDistance(p) <= maxDistance
                        && (SQL_INSTR_PRESENT || nameFilter == null || p.getName().toUpperCase().contains(nameFilter))) {
                    res.add(p);
                    // ensure size limit, discard farest
                    if (res.size() > MAX_NEAR_RESULT) {
                        final PlacemarkSearchResult placemarkToDiscard = res.last();
                        res.remove(placemarkToDiscard);
                        // update search range to search closer
                        maxDistance = locationComparator.calculateDistance(res.last());
                    }
                }
                cursor.moveToNext();
            }
        } finally {
            cursor.close();
        }
        return res;
    }

    public Placemark getPlacemark(final long id) {
        final Cursor cursor = database.query("PLACEMARK", null,
                "_ID=" + id, null, null, null, null);
        try {
            cursor.moveToFirst();
            return cursor.isAfterLast() ? null : cursorToPlacemark(cursor);
        } finally {
            cursor.close();
        }
    }

    /**
     * Get annotation for a placemark
     *
     * @return annotaion for a placemark
     */
    public PlacemarkAnnotation loadPlacemarkAnnotation(PlacemarkBase placemark) {
        final Cursor cursor = database.query("PLACEMARK_ANNOTATION", null,
                "latitude=" + coordinateToInt(placemark.getLatitude()) + " AND longitude=" + coordinateToInt(placemark.getLongitude()), null,
                null, null, null);
        try {
            PlacemarkAnnotation res = null;
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                res = cursorToPlacemarkAnnotation(cursor);
            }
            cursor.close();

            if (res == null) {
                res = new PlacemarkAnnotation();
                res.setLatitude(placemark.getLatitude());
                res.setLongitude(placemark.getLongitude());
                res.setNote("");
            }
            return res;
        } finally {
            cursor.close();
        }
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

    private ContentValues placemarkToContentValues(final Placemark p) {
        final ContentValues cv = new ContentValues();
        cv.put("latitude", coordinateToInt(p.getLatitude()));
        cv.put("longitude", coordinateToInt(p.getLongitude()));
        cv.put("name", p.getName());
        cv.put("description", p.getDescription());
        cv.put("collection_id", p.getCollectionId());
        return cv;
    }

    private ContentValues placemarkAnnotationToContentValues(final PlacemarkAnnotation pa) {
        final ContentValues cv = new ContentValues();
        cv.put("latitude", coordinateToInt(pa.getLatitude()));
        cv.put("longitude", coordinateToInt(pa.getLongitude()));
        cv.put("note", pa.getNote());
        cv.put("flag", pa.isFlagged() ? 1 : 0);
        return cv;
    }

    private Placemark cursorToPlacemark(Cursor cursor) {
        final Placemark p = new Placemark();
        p.setId(cursor.getLong(0));
        p.setLatitude(coordinateToFloat(cursor.getInt(1)));
        p.setLongitude(coordinateToFloat(cursor.getInt(2)));
        p.setName(cursor.getString(3));
        p.setDescription(cursor.getString(4));
        p.setCollectionId(cursor.getLong(5));
        return p;
    }

    private PlacemarkSearchResult cursorToPlacemarkSearchResult(Cursor cursor) {
        return new PlacemarkSearchResult(cursor.getLong(0),
                coordinateToFloat(cursor.getInt(1)),
                coordinateToFloat(cursor.getInt(2)),
                cursor.getString(3),
                cursor.getInt(4) != 0);
    }

    private PlacemarkAnnotation cursorToPlacemarkAnnotation(Cursor cursor) {
        final PlacemarkAnnotation pa = new PlacemarkAnnotation();
        pa.setId(cursor.getLong(0));
        pa.setLatitude(coordinateToFloat(cursor.getInt(1)));
        pa.setLongitude(coordinateToFloat(cursor.getInt(2)));
        pa.setNote(cursor.getString(3));
        pa.setFlagged(cursor.getInt(4) != 0);
        return pa;
    }
}
