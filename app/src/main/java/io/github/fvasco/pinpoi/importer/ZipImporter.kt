package io.github.fvasco.pinpoi.importer

import android.util.Log
import io.github.fvasco.pinpoi.util.ZipGuardInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Import ZIP collection and KMZ file

 * @author Francesco Vasco
 */
class ZipImporter : AbstractImporter() {

    @Throws(IOException::class)
    override fun importImpl(inputStream: InputStream) {
        val zipInputStream = ZipInputStream(inputStream)
        var zipEntry: ZipEntry? = zipInputStream.nextEntry
        while (zipEntry != null) {
            val entryName = zipEntry.name
            val filename = entryName.substringAfterLast('/')
            val extension = filename.substringAfterLast('.').lowercase()
            if (!zipEntry.isDirectory && !filename.startsWith(".")
                && (fileFormatFilter == FileFormatFilter.NONE || extension in fileFormatFilter.validExtensions)
            ) {
                val importer = ImporterFacade.createImporter(entryName, null, fileFormatFilter)
                if (importer != null) {
                    Log.d(ZipImporter::class.java.simpleName, "Import entry $entryName")
                    importer.configureFrom(this)
                    importer.importImpl(ZipGuardInputStream(zipInputStream))
                }
            }
            zipEntry = zipInputStream.nextEntry
        }
    }

    companion object {

        fun isZipInputStream(inputStream: InputStream): Boolean {
            if (!inputStream.markSupported()) return false
            inputStream.mark(4)
            try {
                return inputStream.read() == 0x50
                        && inputStream.read() == 0x4B
                        && inputStream.read() == 0x03
                        && inputStream.read() == 0x04
            } finally {
                inputStream.reset()
            }
        }
    }
}
