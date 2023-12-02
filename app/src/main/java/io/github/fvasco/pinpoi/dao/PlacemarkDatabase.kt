package io.github.fvasco.pinpoi.dao

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * POI database

 * @author Francesco Vasco
 */
internal class PlacemarkDatabase(context: Context) :
    SQLiteOpenHelper(context, "Placemark", null, /*version*/ 2) {

    override fun onCreate(db: SQLiteDatabase) {
        // Placemark table
        db.execSQL(
            "CREATE TABLE PLACEMARK (" +
                    "_ID INTEGER primary key autoincrement," +
                    "latitude INTEGER NOT NULL," +
                    "longitude INTEGER NOT NULL," +
                    "name TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "collection_id INTEGER NOT NULL" +
                    ")"
        )
        db.execSQL("CREATE UNIQUE INDEX IDX_PLACEMARK_COLL ON PLACEMARK (collection_id,latitude,longitude)")

        // PlacemarkAnnotation table
        db.execSQL(
            "CREATE TABLE PLACEMARK_ANNOTATION (" +
                    "_ID INTEGER primary key autoincrement," +
                    "latitude INTEGER NOT NULL," +
                    "longitude INTEGER NOT NULL," +
                    "note TEXT NOT NULL," +
                    "flag INTEGER NOT NULL" +
                    ")"
        )
        db.execSQL("CREATE UNIQUE INDEX IDX_PLACEMARK_ANN_COORD ON PLACEMARK_ANNOTATION (latitude,longitude)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) {
            db.execSQL("UPDATE PLACEMARK SET description='' where description is null")
            db.execSQL("UPDATE PLACEMARK_ANNOTATION SET note='' where note is null")
        }
    }
}
