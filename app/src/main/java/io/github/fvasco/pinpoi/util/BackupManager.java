package io.github.fvasco.pinpoi.util;

import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.fvasco.pinpoi.dao.AbstractDao;

/**
 * Create and restore backup
 *
 * @author Francesco Vasco
 */
public class BackupManager {

    public static final File DEFAULT_BACKUP_FILE = new File(Environment.getExternalStorageDirectory(), "pinpoi.backup");
    private final AbstractDao[] daos;

    public BackupManager(final AbstractDao... daos) {
        this.daos = daos;
    }

    public void create(final File file) throws IOException {
        Log.i(BackupManager.class.getSimpleName(), "Create backup " + file.getAbsolutePath());
        final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(file));
        try {
            for (final AbstractDao dao : daos) {
                synchronized (dao) {
                    final File databaseFile;
                    dao.open();
                    SQLiteDatabase database = dao.getDatabase();
                    try {
                        databaseFile = new File(database.getPath());
                    } finally {
                        database.close();
                        dao.close();
                    }
                    final ZipEntry zipEntry = new ZipEntry(databaseFile.getName());
                    zipOutputStream.putNextEntry(zipEntry);
                    final FileInputStream databaseInputStream = new FileInputStream(databaseFile);
                    try {
                        dao.lock();
                        Util.copy(databaseInputStream, zipOutputStream);
                    } finally {
                        try {
                            databaseInputStream.close();
                        } finally {
                            dao.reset();
                        }
                    }
                    zipOutputStream.closeEntry();
                }
            }
        } finally {
            zipOutputStream.close();
        }
        Log.i(BackupManager.class.getSimpleName(), "Created backup " + file.getAbsolutePath() + " size=" + file.length());
    }

    public void restore(final File file) throws IOException {
        Log.i(BackupManager.class.getSimpleName(), "Restore backup " + file.getAbsolutePath() + " size=" + file.length());
        final ZipFile zipFile = new ZipFile(file);
        try {
            for (final AbstractDao dao : daos) {
                synchronized (dao) {
                    final File databasePath;
                    dao.open();
                    final SQLiteDatabase database = dao.getDatabase();
                    try {
                        databasePath = new File(database.getPath());
                    } finally {
                        database.close();
                        dao.close();
                    }
                    try {
                        final String databaseName = databasePath.getName();
                        Log.i(BackupManager.class.getSimpleName(), "restore database " + databaseName);
                        final ZipEntry zipEntry = zipFile.getEntry(databaseName);
                        final FileOutputStream databaseOutputStream = new FileOutputStream(databasePath);
                        try {
                            dao.lock();
                            Util.copy(zipFile.getInputStream(zipEntry), databaseOutputStream);
                        } finally {
                            databaseOutputStream.close();
                        }
                    } finally {
                        dao.reset();
                    }
                }
            }
        } finally {
            zipFile.close();
        }
        Log.i(BackupManager.class.getSimpleName(), "Restored backup " + file.getAbsolutePath());
    }
}