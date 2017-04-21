package io.github.fvasco.pinpoi.dao

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.fvasco.pinpoi.util.assertDebug

/**
 * Generic Dao.
 *
 * @author Francesco Vasco
 */
abstract class AbstractDao(private val context: Context) {
    var database: SQLiteDatabase? = null
        protected set
    @Volatile private var openCount: Int = 0
    protected var sqLiteOpenHelper: SQLiteOpenHelper = createSqLiteOpenHelper(context)

    protected abstract fun createSqLiteOpenHelper(context: Context): SQLiteOpenHelper

    @Synchronized @Throws(SQLException::class)
    fun open() {
        check(openCount >= 0) { "Database locked" }

        if (openCount == 0) {
            assertDebug(database == null)
            database = sqLiteOpenHelper.writableDatabase
        }
        assertDebug(database != null)
        ++openCount
    }

    @Synchronized fun close() {
        check(openCount > 0)

        --openCount
        if (openCount == 0) {
            database?.close()
            database = null
        } else
            assertDebug(database != null)

    }

    /**
     * Lock database, use [.reset] to unlock
     */
    @Synchronized fun lock() {
        check(openCount == 0) { "Database is open" }

        sqLiteOpenHelper.close()
        assertDebug(database == null)
        openCount = -1
    }

    /**
     * Reinitialize dao state

     * @throws IllegalStateException Error if dao instance is open
     */
    @Synchronized
    fun reset() {
        check(openCount == -1) { "Dao not locked" }
        sqLiteOpenHelper.close()
        sqLiteOpenHelper = createSqLiteOpenHelper(context)
        openCount = 0
    }
}
