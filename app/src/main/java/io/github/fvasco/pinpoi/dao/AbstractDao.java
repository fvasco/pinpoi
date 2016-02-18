package io.github.fvasco.pinpoi.dao;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.design.BuildConfig;

import java.util.Objects;

/**
 * Generic Dao.
 *
 * @author Francesco Vasco
 */
public abstract class AbstractDao<T extends AbstractDao> implements AutoCloseable {
    private final Context context;
    protected SQLiteDatabase database;
    private SQLiteOpenHelper sqLiteOpenHelper;
    private volatile int openCount = 0;

    public AbstractDao(@NonNull final Context context) {
        Objects.requireNonNull(context);
        this.context = context;
        reset();
    }

    protected abstract SQLiteOpenHelper createSqLiteOpenHelper(@NonNull Context context);

    public synchronized T open() throws SQLException {
        //noinspection PointlessBooleanExpression
        if (BuildConfig.DEBUG && openCount < 0) {
            throw new AssertionError(openCount);
        }
        if (openCount == 0) {
            //noinspection PointlessBooleanExpression
            if (BuildConfig.DEBUG && database != null) {
                throw new AssertionError();
            }
            database = sqLiteOpenHelper.getWritableDatabase();
        }
        //noinspection PointlessBooleanExpression
        if (BuildConfig.DEBUG && database == null) {
            throw new AssertionError(openCount);
        }
        ++openCount;
        return (T) this;
    }

    public synchronized void close() {
        //noinspection PointlessBooleanExpression
        if (BuildConfig.DEBUG && openCount <= 0) {
            throw new AssertionError(openCount);
        }
        --openCount;
        if (openCount == 0) {
            database.close();
            database = null;
        } else //noinspection PointlessBooleanExpression
            if (BuildConfig.DEBUG && database == null) {
                throw new AssertionError(openCount);
            }
    }

    /**
     * Reinitialize dao state
     *
     * @throws IllegalStateException Error if dao instance is open
     */
    public synchronized void reset() throws IllegalStateException {
        if (openCount != 0) {
            throw new IllegalStateException("Dao in use");
        }
        sqLiteOpenHelper = createSqLiteOpenHelper(context);
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
