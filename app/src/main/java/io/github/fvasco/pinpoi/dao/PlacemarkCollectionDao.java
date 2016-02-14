package io.github.fvasco.pinpoi.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.Util;

/**
 * Dao for {@linkplain io.github.fvasco.pinpoi.model.PlacemarkCollection}
 *
 * @author Francesco Vasco
 */
public class PlacemarkCollectionDao extends AbstractDao<PlacemarkCollectionDao> {

    private static PlacemarkCollectionDao INSTANCE;

    PlacemarkCollectionDao() {
        this(Util.getApplicationContext());
    }

    public PlacemarkCollectionDao(Context context) {
        setSqLiteOpenHelper(new PlacemarkCollectionDatabase(context));
    }

    public static synchronized PlacemarkCollectionDao getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlacemarkCollectionDao();
        }
        return INSTANCE;
    }

    public PlacemarkCollection findPlacemarkCollectionById(final long id) {
        try (final Cursor cursor = database.query("PLACEMARK_COLLECTION",
                null, "_ID=" + id, null, null, null, null)) {
            cursor.moveToFirst();
            return cursor.isAfterLast() ? null : cursorToPlacemarkCollection(cursor);
        }
    }

    public List<String> findAllPlacemarkCollectionCategory() {
        try (final Cursor cursor = database.query(true, "PLACEMARK_COLLECTION",
                new String[]{"CATEGORY"}, "length(CATEGORY)>0", null, "CATEGORY", null, "CATEGORY", null)) {
            final List<String> res = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                res.add(cursor.getString(0));
                cursor.moveToNext();
            }
            return res;
        }
    }

    public List<PlacemarkCollection> findAllPlacemarkCollectionInCategory(String selectedPlacemarkCategory) {
        try (final Cursor cursor = database.query("PLACEMARK_COLLECTION",
                null, "CATEGORY=?", new String[]{selectedPlacemarkCategory}, null, null, "NAME")) {
            final List<PlacemarkCollection> res = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                res.add(cursorToPlacemarkCollection(cursor));
                cursor.moveToNext();
            }
            return res;
        }

    }

    public List<PlacemarkCollection> findAllPlacemarkCollection() {
        try (final Cursor cursor = database.query("PLACEMARK_COLLECTION",
                null, null, null, null, null, "CATEGORY,NAME")) {
            final List<PlacemarkCollection> res = new ArrayList<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                res.add(cursorToPlacemarkCollection(cursor));
                cursor.moveToNext();
            }
            return res;
        }

    }

    public void insert(PlacemarkCollection pc) {
        final long id = database.insert("PLACEMARK_COLLECTION", null, placemarkCollectionToContentValues(pc));
        if (id == -1) {
            throw new IllegalArgumentException("Data not valid");
        }
        pc.setId(id);
    }

    public void update(PlacemarkCollection pc) {
        database.update("PLACEMARK_COLLECTION", placemarkCollectionToContentValues(pc), "_ID=" + pc.getId(), null);
    }

    public void delete(PlacemarkCollection pc) {
        database.delete("PLACEMARK_COLLECTION", "_ID=" + pc.getId(), null);
    }

    private ContentValues placemarkCollectionToContentValues(final PlacemarkCollection pc) {
        final ContentValues cv = new ContentValues();
        cv.put("name", pc.getName());
        cv.put("description", pc.getDescription());
        cv.put("source", pc.getSource());
        cv.put("category", pc.getCategory() == null ? null : pc.getCategory().toUpperCase());
        cv.put("last_update", pc.getLastUpdate());
        cv.put("poi_count", pc.getPoiCount());
        return cv;
    }

    private PlacemarkCollection cursorToPlacemarkCollection(Cursor cursor) {
        final PlacemarkCollection pc = new PlacemarkCollection();
        pc.setId(cursor.getLong(0));
        pc.setName(cursor.getString(1));
        pc.setDescription(cursor.getString(2));
        pc.setSource(cursor.getString(3));
        pc.setCategory(cursor.getString(4));
        pc.setLastUpdate(cursor.getLong(5));
        pc.setPoiCount(cursor.getInt(6));
        return pc;
    }

}
