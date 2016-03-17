package io.github.fvasco.pinpoi.dao

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.fvasco.pinpoi.BuildConfig

/**
 * Generic Dao.

 * @author Francesco Vasco
 */
abstract class AbstractDao(private val context: Context) {
    var database: SQLiteDatabase? = null
        protected set
    protected var sqLiteOpenHelper: SQLiteOpenHelper
    @Volatile private var openCount: Int = 0

    init {
        sqLiteOpenHelper = createSqLiteOpenHelper(context)
    }

    protected abstract fun createSqLiteOpenHelper(context: Context): SQLiteOpenHelper

    @Synchronized @Throws(SQLException::class)
    fun open() {
        if (openCount < 0) error("Database locked")

        if (openCount == 0) {
            //noinspection PointlessBooleanExpression
            if (BuildConfig.DEBUG && database != null) {
                throw AssertionError()
            }
            database = sqLiteOpenHelper.writableDatabase
        }
        //noinspection PointlessBooleanExpression
        if (BuildConfig.DEBUG && database == null) {
            throw AssertionError(openCount)
        }
        ++openCount
    }

    @Synchronized fun close() {
        if (openCount <= 0) error(openCount)

        --openCount
        if (openCount == 0) {
            database?.close()
            database = null
        } else //noinspection PointlessBooleanExpression
            if (BuildConfig.DEBUG && database == null) {
                throw AssertionError(openCount)
            }
    }

    /**
     * Lock database, use [.reset] to unlock
     */
    @Synchronized fun lock() {
        if (openCount > 0) error("Database is open")

        sqLiteOpenHelper.close()
        if (BuildConfig.DEBUG && database != null) throw AssertionError()
        openCount = -1
    }

    /**
     * Reinitialize dao state

     * @throws IllegalStateException Error if dao instance is open
     */
    @Synchronized @Throws(IllegalStateException::class)
    fun reset() {
        if (openCount > 0) {
            throw IllegalStateException("Dao in use")
        }
        sqLiteOpenHelper.close()
        sqLiteOpenHelper = createSqLiteOpenHelper(context)
        openCount = 0
    }

}
