package io.github.fvasco.pinpoi.dao

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteOpenHelper
import io.github.fvasco.pinpoi.importer.FileFormatFilter
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.Util
import java.util.*

/**
 * Dao for [io.github.fvasco.pinpoi.model.PlacemarkCollection]

 * @author Francesco Vasco
 */
class PlacemarkCollectionDao(context: Context) : AbstractDao(context) {

    internal constructor() : this(Util.applicationContext) {
    }

    override fun createSqLiteOpenHelper(context: Context): SQLiteOpenHelper {
        return PlacemarkCollectionDatabase(context)
    }

    fun findPlacemarkCollectionById(id: Long): PlacemarkCollection? {
        database!!.query("PLACEMARK_COLLECTION",
                null, "_ID=" + id, null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            return if (cursor.isAfterLast) null else cursorToPlacemarkCollection(cursor)
        }
    }

    fun findPlacemarkCollectionByName(name: String): PlacemarkCollection? {
        database!!.query("PLACEMARK_COLLECTION",
                null, "NAME=?", arrayOf(name), null, null, null).use { cursor ->
            cursor.moveToFirst()
            return if (cursor.isAfterLast) null else cursorToPlacemarkCollection(cursor)
        }
    }

    fun findAllPlacemarkCollectionCategory(): List<String> {
        database!!.query(true, "PLACEMARK_COLLECTION",
                arrayOf("CATEGORY"), "length(CATEGORY)>0", null, "CATEGORY", null, "CATEGORY", null).use { cursor ->
            val res = ArrayList<String>()
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                res.add(cursor.getString(0))
                cursor.moveToNext()
            }
            return res
        }
    }

    fun findAllPlacemarkCollectionInCategory(selectedPlacemarkCategory: String): List<PlacemarkCollection> {
        database!!.query("PLACEMARK_COLLECTION",
                null, "CATEGORY=?", arrayOf(selectedPlacemarkCategory), null, null, "NAME").use { cursor ->
            val res = ArrayList<PlacemarkCollection>()
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                res.add(cursorToPlacemarkCollection(cursor))
                cursor.moveToNext()
            }
            return res
        }
    }

    fun findAllPlacemarkCollection(): List<PlacemarkCollection> {
        database!!.query("PLACEMARK_COLLECTION",
                null, null, null, null, null, "CATEGORY,NAME").use { cursor ->
            val res = ArrayList<PlacemarkCollection>()
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                res.add(cursorToPlacemarkCollection(cursor))
                cursor.moveToNext()
            }
            return res
        }
    }

    fun insert(pc: PlacemarkCollection) {
        val id = database!!.insert("PLACEMARK_COLLECTION", null, placemarkCollectionToContentValues(pc))
        require(id != -1L) { "Data not valid" }
        pc.id = id
    }

    fun update(pc: PlacemarkCollection) {
        database!!.update("PLACEMARK_COLLECTION", placemarkCollectionToContentValues(pc), "_ID=" + pc.id, null)
    }

    fun delete(pc: PlacemarkCollection) {
        database!!.delete("PLACEMARK_COLLECTION", "_ID=" + pc.id, null)
    }

    private fun placemarkCollectionToContentValues(pc: PlacemarkCollection): ContentValues {
        val cv = ContentValues()
        cv.put("name", pc.name.trim())
        cv.put("description", pc.description.trim())
        cv.put("source", pc.source.trim())
        cv.put("category", pc.category.trim().toUpperCase())
        cv.put("fileFormatFilter", pc.fileFormatFilter.toString())
        cv.put("last_update", pc.lastUpdate)
        cv.put("poi_count", pc.poiCount)
        return cv
    }

    private fun cursorToPlacemarkCollection(cursor: Cursor): PlacemarkCollection {
        val pc = PlacemarkCollection()
        pc.id = cursor.getLong(0)
        pc.name = cursor.getString(1)
        pc.description = cursor.getString(2)
        pc.source = cursor.getString(3)
        pc.category = cursor.getString(4)
        pc.lastUpdate = cursor.getLong(5)
        pc.poiCount = cursor.getInt(6)
        pc.fileFormatFilter = FileFormatFilter.valueOf(cursor.getString(7) ?: FileFormatFilter.NONE.toString())
        return pc
    }

    companion object {
        val instance: PlacemarkCollectionDao by lazy { PlacemarkCollectionDao() }
    }

}
