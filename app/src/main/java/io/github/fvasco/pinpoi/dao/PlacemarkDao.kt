package io.github.fvasco.pinpoi.dao

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation
import io.github.fvasco.pinpoi.model.PlacemarkBase
import io.github.fvasco.pinpoi.model.PlacemarkSearchResult
import io.github.fvasco.pinpoi.util.Coordinates
import io.github.fvasco.pinpoi.util.PlacemarkDistanceComparator
import io.github.fvasco.pinpoi.util.distanceTo
import java.util.SortedSet
import java.util.TreeSet
import kotlin.math.roundToInt

/**
 * Dao for [io.github.fvasco.pinpoi.model.Placemark]
 *
 * @author Francesco Vasco
 */
/*
 * Save coordinate as int: coordinate*{@linkplain #COORDINATE_MULTIPLIER}
 */
class PlacemarkDao(context: Context) : AbstractDao(context) {

    override fun createSqLiteOpenHelper(context: Context): SQLiteOpenHelper {
        return PlacemarkDatabase(context)
    }

    fun isEmpty(): Boolean =
        database!!.query("PLACEMARK", arrayOf("_ID"), null, null, null, null, null, "1")
            .use { cursor -> cursor.isAfterLast }

    fun findAllPlacemarkByCollectionId(collectionId: Long): List<Placemark> {
        database!!.query(
            "PLACEMARK", null,
            "collection_id=$collectionId", null, null, null, "_ID"
        ).use { cursor ->
            val res = ArrayList<Placemark>()
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                res.add(cursorToPlacemark(cursor))
                cursor.moveToNext()
            }
            return res
        }
    }

    /**
     * Search [Placemark] near location
     *
     * @param coordinates   the center of search
     * @param range         radius of search, in meters
     * @param collectionIds collection id filter
     */
    fun findAllPlacemarkNear(
        coordinates: Coordinates,
        range: Double,
        collectionIds: Collection<Long>,
        nameFilter: String? = null,
        onlyFavourite: Boolean = false
    ): SortedSet<PlacemarkSearchResult> {
        require(collectionIds.isNotEmpty()) { "collection empty" }
        require(range > 0) { "range not valid $range" }


        // sql clause
        // collection ids
        val sql = StringBuilder(
            "SELECT p._ID,p.latitude,p.longitude,p.name,pa.flag,pa.note,collection_id FROM PLACEMARK p" +
                    " LEFT OUTER JOIN PLACEMARK_ANNOTATION pa USING(latitude,longitude)" +
                    " WHERE p.collection_id in ("
        )
        val whereArgs = ArrayList<String>()
        val iterator = collectionIds.iterator()
        sql.append(iterator.next().toString())
        while (iterator.hasNext()) {
            sql.append(',').append(iterator.next().toString())
        }
        sql.append(") AND ")
        createWhereFilter(coordinates, range, "p", sql)

        if (onlyFavourite) {
            sql.append(" AND pa.flag=1")
        }

        if (!nameFilter.isNullOrBlank()) {
            sql.append(" AND instr(upper(name),?)>0")
            whereArgs.add(nameFilter.uppercase())
        }

        val locationComparator = PlacemarkDistanceComparator(coordinates)
        val res = TreeSet(locationComparator)
        database!!.rawQuery(sql.toString(), whereArgs.toTypedArray()).use { cursor ->
            cursor.moveToFirst()
            var maxDistance = range
            while (!cursor.isAfterLast) {
                val p = cursorToPlacemarkSearchResult(cursor)
                if (coordinates.distanceTo(p.coordinates) <= maxDistance) {
                    res.add(p)
                    // ensure size limit, discard the most distant
                    if (res.size > MAX_NEAR_RESULT) {
                        val placemarkToDiscard = res.last()
                        res.remove(placemarkToDiscard)
                        // update search range to search closer
                        maxDistance = coordinates.distanceTo(res.last().coordinates).toDouble()
                    }
                }
                cursor.moveToNext()
            }
        }
        return res
    }

    fun getPlacemark(id: Long): Placemark? {
        if (id <= 0) return null
        database!!.query(
            "PLACEMARK", null,
            "_ID=$id", null, null, null, null
        ).use { cursor ->
            cursor.moveToFirst()
            return if (cursor.isAfterLast) null else cursorToPlacemark(cursor)
        }
    }

    /**
     * Get annotation for a placemark

     * @return annotaion for a placemark
     */
    fun loadPlacemarkAnnotation(placemark: PlacemarkBase): PlacemarkAnnotation {
        val latitude = placemark.coordinates.latitude
        val longitude = placemark.coordinates.longitude
        database!!.query(
            "PLACEMARK_ANNOTATION",
            null,
            "latitude=" + coordinateToInt(latitude) + " AND longitude=" + coordinateToInt(longitude),
            null,
            null,
            null,
            null
        ).use { cursor ->
            cursor.moveToFirst()
            return if (cursor.isAfterLast) {
                PlacemarkAnnotation(coordinates = Coordinates(latitude, longitude))
            } else {
                cursorToPlacemarkAnnotation(cursor)
            }
        }
    }

    fun update(placemarkAnnotation: PlacemarkAnnotation) {
        val whereClause =
            "latitude=${coordinateToInt(placemarkAnnotation.coordinates.latitude)} AND longitude=${
                coordinateToInt(placemarkAnnotation.coordinates.longitude)
            }"
        if (placemarkAnnotation.note.isBlank() && !placemarkAnnotation.flagged) {
            database!!.delete(
                "PLACEMARK_ANNOTATION",
                whereClause,
                null
            )
        } else {
            val contentValues = placemarkAnnotationToContentValues(placemarkAnnotation)
            val count = database!!.update("PLACEMARK_ANNOTATION", contentValues, whereClause, null)
            if (count == 0) {
                database!!.insertOrThrow("PLACEMARK_ANNOTATION", null, contentValues)
            }
        }
    }

    fun insert(p: Placemark, conflictAlgorithm: Int = SQLiteDatabase.CONFLICT_FAIL): Boolean {
        val id = database!!.insertWithOnConflict(
            "PLACEMARK", null,
            placemarkToContentValues(p), conflictAlgorithm
        )
        return id > 0
    }

    fun deleteByCollectionId(collectionId: Long) {
        database!!.delete("PLACEMARK", "collection_id=$collectionId", null)
    }

    private fun placemarkToContentValues(p: Placemark): ContentValues {
        val c = p.coordinates
        val cv = ContentValues()
        cv.put("latitude", coordinateToInt(c.latitude))
        cv.put("longitude", coordinateToInt(c.longitude))
        cv.put("name", p.name.trim())
        cv.put("description", p.description.trim())
        cv.put("collection_id", p.collectionId)
        return cv
    }

    private fun placemarkAnnotationToContentValues(pa: PlacemarkAnnotation): ContentValues {
        val ca = pa.coordinates
        val cv = ContentValues()
        cv.put("latitude", coordinateToInt(ca.latitude))
        cv.put("longitude", coordinateToInt(ca.longitude))
        cv.put("note", pa.note.trim())
        cv.put("flag", if (pa.flagged) 1 else 0)
        return cv
    }

    private fun cursorToPlacemark(cursor: Cursor) =
        Placemark(
            id = cursor.getLong(0),
            coordinates = Coordinates(
                coordinateToFloat(cursor.getInt(1)),
                coordinateToFloat(cursor.getInt(2))
            ),
            name = cursor.getString(3),
            description = cursor.getString(4),
            collectionId = cursor.getLong(5)
        )

    private fun cursorToPlacemarkSearchResult(cursor: Cursor) =
        PlacemarkSearchResult(
            id = cursor.getLong(0),
            coordinates = Coordinates(
                coordinateToFloat(cursor.getInt(1)),
                coordinateToFloat(cursor.getInt(2))
            ),
            name = cursor.getString(3),
            note = cursor.getString(5)?.takeIf(String::isNotBlank),
            flagged = cursor.getInt(4) != 0,
            collectionId = cursor.getLong(6)
        )

    private fun cursorToPlacemarkAnnotation(cursor: Cursor) =
        PlacemarkAnnotation(
            // column 0 is id (ignored)
            coordinates = Coordinates(
                coordinateToFloat(cursor.getInt(1)),
                coordinateToFloat(cursor.getInt(2))
            ),
            note = cursor.getString(3),
            flagged = cursor.getInt(4) != 0
        )

    companion object {

        /**
         * Max result for [findAllPlacemarkNear]
         */
        private const val MAX_NEAR_RESULT = 300

        // 2^20
        private const val COORDINATE_MULTIPLIER = 1048576f

        /**
         * Convert DB coordinates to double

         * @param i db coordinate
         * *
         * @return float coordinates
         */
        private fun coordinateToFloat(i: Int): Float {
            return i / COORDINATE_MULTIPLIER
        }

        /**
         * Convert double to db coordinates

         * @param f float coordinate
         * *
         * @return db coordinates
         */
        private fun coordinateToInt(f: Float): Int = coordinateToInt(f.toDouble())

        private fun coordinateToInt(f: Double): Int {
            return (f * COORDINATE_MULTIPLIER).roundToInt()
        }

        /**
         * Append coordinates filter in stringBuilder sql clause
         */
        private fun createWhereFilter(
            coordinates: Coordinates,
            range: Double,
            table: String,
            stringBuilder: StringBuilder
        ) {
            // calculate "square" of search
            val shiftY =
                coordinates.copy(latitude = coordinates.latitude + if (coordinates.latitude > 0) -1 else 1)
            val scaleY = coordinates.distanceTo(shiftY)
            val shiftX =
                coordinates.copy(longitude = coordinates.longitude + if (coordinates.longitude > 0) -1 else 1)
            val scaleX = coordinates.distanceTo(shiftX)

            // latitude
            stringBuilder.append(table).append(".latitude between ")
                .append(coordinateToInt(coordinates.latitude - range / scaleY).toString())
                .append(" AND ")
                .append(coordinateToInt(coordinates.latitude + range / scaleY).toString())

            // longitude
            val longitudeMin = coordinates.longitude - range / scaleX
            val longitudeMax = coordinates.longitude + range / scaleX
            stringBuilder.append(" AND (").append(table).append(".longitude between ")
                .append(coordinateToInt(longitudeMin).toString()).append(" AND ")
                .append(coordinateToInt(longitudeMax).toString())
            // fix for meridian 180
            if (longitudeMin < -180.0) {
                stringBuilder.append(" OR ").append(table).append(".longitude >=")
                    .append(coordinateToInt(longitudeMin + 360.0).toString())
            } else if (longitudeMax > 180.0) {
                stringBuilder.append(" OR ").append(table).append(".longitude <=")
                    .append(coordinateToInt(longitudeMax - 360.0).toString())
            }
            stringBuilder.append(')')
        }
    }
}
