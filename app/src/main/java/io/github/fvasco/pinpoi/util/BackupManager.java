package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.fvasco.pinpoi.dao.AbstractDao;
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;

/**
 * Create and restore backup
 *
 * @author Francesco Vasco
 */
public class BackupManager {

    public static final File BACKUP_FILE = new File(Environment.getExternalStorageDirectory(), "pinpoi.backup");
    private static final File BACKUP_DIR = Environment.getExternalStorageDirectory();
    private final AbstractDao[] daos;

    public BackupManager(final Context context) {
        daos = new AbstractDao[]{
                new PlacemarkCollectionDao(context),
                new PlacemarkDao(context)
        };
    }

    public static boolean isRestoreBackupSupported() {
        return BACKUP_DIR.isDirectory() && BACKUP_DIR.canRead();
    }

    public static boolean isCreateBackupSupported() {
        return BACKUP_DIR.isDirectory() && BACKUP_DIR.canWrite();
    }

    public void create() throws IOException {
        if (!isCreateBackupSupported()) {
            throw new IllegalStateException();
        }
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(BACKUP_FILE))) {
            for (final AbstractDao dao : daos) {
                dao.open();
                try (SQLiteDatabase database = dao.getDatabase()) {
                    final File databasePath = new File(database.getPath());
                    final ZipEntry zipEntry = new ZipEntry(databasePath.getName());
                    zipOutputStream.putNextEntry(zipEntry);
                    try (final InputStream inputStream = new FileInputStream(databasePath)) {
                        Util.copy(inputStream, zipOutputStream);
                    }
                } finally {
                    dao.close();
                }
                zipOutputStream.closeEntry();
            }
        }
    }

    public void restore() throws IOException {
        if (!BACKUP_FILE.isFile() || !BACKUP_FILE.canRead()) {
            throw new IllegalStateException("Can't read" + BACKUP_FILE.getAbsolutePath());
        }
        try (final ZipFile zipFile = new ZipFile(BACKUP_FILE)) {
            for (final AbstractDao dao : daos) {
                dao.open();
                try (SQLiteDatabase database = dao.getDatabase()) {
                    final File databasePath = new File(database.getPath());
                    final String databaseName = databasePath.getName();
                    Log.i(BackupManager.class.getSimpleName(), "restore database " + databaseName);
                    final InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(databaseName));
                    try (final OutputStream outputStream = new FileOutputStream(databasePath)) {
                        Util.copy(inputStream, outputStream);
                    }
                } finally {
                    dao.close();
                }
            }
        }
    }
}