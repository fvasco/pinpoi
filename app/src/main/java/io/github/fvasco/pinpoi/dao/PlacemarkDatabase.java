package io.github.fvasco.pinpoi.dao;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * POI database
 *
 * @author Francesco Vasco
 */
class PlacemarkDatabase extends SQLiteOpenHelper {

    public static final int VERSION = 1;

    public PlacemarkDatabase(Context context) {
        super(context, "Placemark", null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE PLACEMARK (" +
                "_ID INTEGER primary key autoincrement," +
                "longitude REAL NOT NULL," +
                "latitude REAL NOT NULL" +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "collection_id INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IDX_PLACEMARK_COLL ON PLACEMARK (collection_id,longitude,latitude)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
