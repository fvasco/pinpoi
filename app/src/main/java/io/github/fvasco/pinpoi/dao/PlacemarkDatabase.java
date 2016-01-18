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
        // Placemark table
        db.execSQL("CREATE TABLE PLACEMARK (" +
                "_ID INTEGER primary key autoincrement," +
                "latitude INTEGER NOT NULL," +
                "longitude INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "collection_id INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IDX_PLACEMARK_COLL ON PLACEMARK (collection_id,longitude,latitude)");

        // PlacemarkAnnotation table
        db.execSQL("CREATE TABLE PLACEMARK_ANNOTATION (" +
                "_ID INTEGER primary key autoincrement," +
                "latitude INTEGER NOT NULL," +
                "longitude INTEGER NOT NULL," +
                "note TEXT NOT NULL," +
                "flag INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX IDX_PLACEMARK_ANN_COORD ON PLACEMARK_ANNOTATION (longitude,latitude)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
