package io.github.fvasco.pinpoi.dao;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Generic Dao.
 * Init with {@linkplain #setSqLiteOpenHelper(SQLiteOpenHelper)}.
 * {@linkplain #close()} to free resources
 *
 * @author Francesco Vasco
 */
public abstract class AbstractDao implements AutoCloseable {
    protected SQLiteDatabase database;
    private SQLiteOpenHelper sqLiteOpenHelper;
    private int openCount;

    public SQLiteOpenHelper getSqLiteOpenHelper() {
        return sqLiteOpenHelper;
    }

    protected void setSqLiteOpenHelper(SQLiteOpenHelper sqLiteOpenHelper) {
        if (this.sqLiteOpenHelper != null) {
            throw new IllegalStateException("sqLiteOpenHelper already defined");
        }
        this.sqLiteOpenHelper = sqLiteOpenHelper;
    }

    public synchronized void open() throws SQLException {
        if (database == null) {
            database = sqLiteOpenHelper.getWritableDatabase();
            openCount = 0;
        }
        ++openCount;
    }

    public synchronized void close() {
        --openCount;
        if (openCount == 0) {
            database.close();
            database = null;
        }
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}
