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
                "_ID integer primary key autoincrement," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "source TEXT NOT NULL," +
                "category TEXT" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
