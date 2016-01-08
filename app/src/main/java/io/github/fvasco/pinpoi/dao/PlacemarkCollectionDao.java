package io.github.fvasco.pinpoi.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * Dao for {@linkplain io.github.fvasco.pinpoi.model.PlacemarkCollection}
 *
 * @author Francesco Vasco
 */
public class PlacemarkCollectionDao extends AbstractDao {

    private PlacemarkCollectionDatabase dbHelper;

    public PlacemarkCollectionDao(Context context) {
        dbHelper = new PlacemarkCollectionDatabase(context);
        setSqLiteOpenHelper(dbHelper);
    }

    public List<String> getAllPlacemarkCollectionCategory() {
        final List<String> res = new ArrayList<>();
        final Cursor cursor = database.query("PLACEMARK_COLLECTION",
                new String[]{"CATEGORY"}, "CATEGORY", null, null, null, "CATEGORY");

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            res.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return res;
    }

    public List<PlacemarkCollection> getAllPlacemarkCollection() {
        final List<PlacemarkCollection> res = new ArrayList<>();
        final Cursor cursor = database.query("PLACEMARK_COLLECTION",
                null, null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            res.add(cursorToPlacemarkCollection(cursor));
            cursor.moveToNext();
        }
        cursor.close();
        return res;
    }

    public void insert(PlacemarkCollection pc) {
        database.insert("PLACEMARK_COLLECTION", null, placemarkCollectionToContentValues(pc));
    }

    public void update(PlacemarkCollection pc) {
        database.update("PLACEMARK_COLLECTION", placemarkCollectionToContentValues(pc), "_ID=" + pc.getId(), null);
    }

    public void delete(PlacemarkCollection pc) {
        database.delete("PLACEMARK_COLLECTION", "_ID=" + pc.getId(), null);
    }

    ContentValues placemarkCollectionToContentValues(final PlacemarkCollection pc) {
        final ContentValues cv = new ContentValues();
        if (pc.getId() > 0) {
            cv.put("_ID", pc.getId());
        }
        cv.put("name", pc.getName());
        cv.put("description", pc.getDescription());
        cv.put("source", pc.getSource());
        cv.put("category", pc.getCategory());
        return cv;
    }

    PlacemarkCollection cursorToPlacemarkCollection(Cursor cursor) {
        final PlacemarkCollection pc = new PlacemarkCollection();
        pc.setId(cursor.getLong(0));
        pc.setName(cursor.getString(1));
        pc.setDescription(cursor.getString(2));
        pc.setSource(cursor.getString(3));
        pc.setCategory(cursor.getString(4));
        return pc;
    }
}
