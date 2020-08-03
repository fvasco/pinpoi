package io.github.fvasco.pinpoi.util

import android.util.Log
import io.github.fvasco.pinpoi.dao.AbstractDao
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Create and restore backup

 * @author Francesco Vasco
 */
class BackupManager(private vararg val daos: AbstractDao) {

    @Throws(IOException::class)
    fun create(outputStream: OutputStream) {
        Log.i(BackupManager::class.java.simpleName, "Create backup $outputStream")
        ZipOutputStream(outputStream).use { zipOutputStream ->
            for (dao in daos) {
                synchronized(dao) {
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
        }
    }

    @Throws(IOException::class)
    fun restore(fileInputStream: InputStream) {
        Log.i(BackupManager::class.java.simpleName, "Restore backup $fileInputStream")
        ZipInputStream(fileInputStream).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                for (dao in daos) {
                    synchronized(dao) {
                        val databasePath: File
                        dao.open()
                        val database = dao.database!!
                        try {
                            databasePath = File(database.path)
                        } finally {
                            database.close()
                            dao.close()
                        }
                        val databaseName = databasePath.name
                        if (databaseName == zipEntry.name) {
                            try {
                                Log.i(BackupManager::class.java.simpleName, "restore database $databaseName")
                                FileOutputStream(databasePath).use { databaseOutputStream ->
                                    dao.lock()
                                    ZipGuardInputStream(zipInputStream).copyTo(databaseOutputStream)
                                }
                            } finally {
                                dao.reset()
                            }
                        }
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
    }
}
