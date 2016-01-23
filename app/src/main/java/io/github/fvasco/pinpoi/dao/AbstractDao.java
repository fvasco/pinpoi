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
public abstract class AbstractDao<T extends AbstractDao> implements AutoCloseable {
    protected SQLiteDatabase database;
    private SQLiteOpenHelper sqLiteOpenHelper;
    private int openCount = 0;

    public SQLiteOpenHelper getSqLiteOpenHelper() {
        return sqLiteOpenHelper;
    }

    protected void setSqLiteOpenHelper(SQLiteOpenHelper sqLiteOpenHelper) {
        if (this.sqLiteOpenHelper != null) {
            throw new IllegalStateException("sqLiteOpenHelper already defined");
        }
        this.sqLiteOpenHelper = sqLiteOpenHelper;
    }

    public synchronized T open() throws SQLException {
        assert openCount >= 0;
        if (openCount == 0) {
            assert database == null;
            database = sqLiteOpenHelper.getWritableDatabase();
        }
        assert database != null;
        ++openCount;
        return (T) this;
    }

    public synchronized void close() {
        assert openCount > 0;
        --openCount;
        if (openCount == 0) {
            database.close();
            database = null;
        } else {
            assert database != null;
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
