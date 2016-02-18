package io.github.fvasco.pinpoi.util;

import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.fvasco.pinpoi.BuildConfig;
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
        try (final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(file))) {
            final WritableByteChannel zipChannel = Channels.newChannel(zipOutputStream);
            for (final AbstractDao dao : daos) {
                synchronized (dao) {
                    final File databaseFile;
                    dao.open();
                    try (SQLiteDatabase database = dao.getDatabase()) {
                        databaseFile = new File(database.getPath());
                    } finally {
                        dao.close();
                    }
                    final ZipEntry zipEntry = new ZipEntry(databaseFile.getName());
                    zipOutputStream.putNextEntry(zipEntry);
                    try (final FileChannel databaseChannel = new FileInputStream(databaseFile).getChannel()) {
                        long count = 0;
                        final long max = databaseFile.length();
                        while (count != max) {
                            count += databaseChannel.transferTo(count, max - count, zipChannel);
                        }
                    }
                    zipOutputStream.closeEntry();
                }
            }
        }
    }

    public void restore(final File file) throws IOException {
        try (final ZipFile zipFile = new ZipFile(file)) {
            for (final AbstractDao dao : daos) {
                synchronized (dao) {
                    final File databasePath;
                    dao.open();
                    try (SQLiteDatabase database = dao.getDatabase()) {
                        databasePath = new File(database.getPath());
                    } finally {
                        dao.close();
                    }
                    try {
                        final String databaseName = databasePath.getName();
                        Log.i(BackupManager.class.getSimpleName(), "restore database " + databaseName);
                        final ZipEntry zipEntry = zipFile.getEntry(databaseName);
                        final ReadableByteChannel entryChannel = Channels.newChannel(zipFile.getInputStream(zipEntry));
                        try (final FileChannel fileChannel = new FileOutputStream(databasePath).getChannel()) {
                            long count = 0;
                            final long max = zipEntry.getSize();
                            fileChannel.truncate(max);
                            while (count != max) {
                                count += fileChannel.transferFrom(entryChannel, count, max - count);
                            }
                            if (BuildConfig.DEBUG && databasePath.length() != zipEntry.getSize()) {
                                throw new IOException("Backup failed");
                            }
                        }
                    } finally {
                        dao.reset();
                    }
                }
            }
        }
    }
}