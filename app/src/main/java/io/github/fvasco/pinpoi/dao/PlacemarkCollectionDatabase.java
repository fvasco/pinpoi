package io.github.fvasco.pinpoi.dao;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Placemark collection database
 *
 * @author Francesco Vasco
 */
class PlacemarkCollectionDatabase extends SQLiteOpenHelper {

    public static final int VERSION = 1;

    public PlacemarkCollectionDatabase(Context context) {
        super(context, "PlacemarkCollection", null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE PLACEMARK_COLLECTION (" +
                "_ID INTEGER primary key autoincrement," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "source TEXT NOT NULL," +
                "category TEXT," +
                "last_update INTEGER NOT NULL," +
                "poi_count INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX IDX_PLACEMARK_COLL_NAME ON PLACEMARK_COLLECTION (name)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
