package io.github.fvasco.pinpoi.util

import android.os.Environment
import android.util.Log
import io.github.fvasco.pinpoi.dao.AbstractDao
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Create and restore backup

 * @author Francesco Vasco
 */
class BackupManager(private vararg val daos: AbstractDao) {

    @Throws(IOException::class)
    fun create(file: File) {
        Log.i(BackupManager::class.java.simpleName, "Create backup " + file.absolutePath)
        val zipOutputStream = ZipOutputStream(FileOutputStream(file))
        try {
            for (dao in daos) {
                synchronized (dao) {
                    val databaseFile: File
                    dao.open()
                    val database = dao.database!!
                    try {
                        databaseFile = File(database.path)
                    } finally {
                        database.close()
                        dao.close()
                    }
                    val zipEntry = ZipEntry(databaseFile.name)
                    zipOutputStream.putNextEntry(zipEntry)
                    val databaseInputStream = FileInputStream(databaseFile)
                    try {
                        dao.lock()
                        databaseInputStream.copyTo(zipOutputStream)
                    } finally {
                        try {
                            databaseInputStream.close()
                        } finally {
                            dao.reset()
                        }
                    }
                    zipOutputStream.closeEntry()
                }
            }
        } finally {
            zipOutputStream.close()
        }
        Log.i(BackupManager::class.java.simpleName, "Created backup " + file.absolutePath + " size=" + file.length())
    }

    @Throws(IOException::class)
    fun restore(file: File) {
        Log.i(BackupManager::class.java.simpleName, "Restore backup " + file.absolutePath + " size=" + file.length())
        val zipFile = ZipFile(file)
        try {
            for (dao in daos) {
                synchronized (dao) {
                    val databasePath: File
                    dao.open()
                    val database = dao.database!!
                    try {
                        databasePath = File(database.path)
                    } finally {
                        database.close()
                        dao.close()
                    }
                    try {
                        val databaseName = databasePath.name
                        Log.i(BackupManager::class.java.simpleName, "restore database " + databaseName)
                        val zipEntry = zipFile.getEntry(databaseName)
                        val databaseOutputStream = FileOutputStream(databasePath)
                        try {
                            dao.lock()
                            zipFile.getInputStream(zipEntry).copyTo(databaseOutputStream)
                        } finally {
                            databaseOutputStream.close()
                        }
                    } finally {
                        dao.reset()
                    }
                }
            }
        } finally {
            zipFile.close()
        }
        Log.i(BackupManager::class.java.simpleName, "Restored backup " + file.absolutePath)
    }

    companion object {
        @JvmStatic
        val DEFAULT_BACKUP_FILE = File(Environment.getExternalStorageDirectory(), "pinpoi.backup")
    }
}