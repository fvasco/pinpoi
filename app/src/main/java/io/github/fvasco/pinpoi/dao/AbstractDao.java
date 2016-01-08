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

    public SQLiteOpenHelper getSqLiteOpenHelper() {
        return sqLiteOpenHelper;
    }

    protected void setSqLiteOpenHelper(SQLiteOpenHelper sqLiteOpenHelper) {
        if (this.sqLiteOpenHelper != null) {
            throw new IllegalStateException("sqLiteOpenHelper defined");
        }
        this.sqLiteOpenHelper = sqLiteOpenHelper;
    }

    public void open() throws SQLException {
        database = sqLiteOpenHelper.getWritableDatabase();
    }

    public void close() {
        try {
            if (database != null) {
                database.close();
                database = null;
            }
        } finally {
            sqLiteOpenHelper.close();
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
