package io.github.fvasco.pinpoi.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.fvasco.pinpoi.BuildConfig
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.*
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue

/**
 * Import [Placemark] and update [PlacemarkCollectionDao]
 *
 * Importer facade for:
 *  * KML
 *  * KMZ
 *  * GPX
 *  * RSS
 *  * OV2 Tomtom
 *  * ASC, CSV files
 * @author Francesco Vasco
 */
class ImporterFacade(context: Context) {

    private val placemarkDao: PlacemarkDao
    private val placemarkCollectionDao: PlacemarkCollectionDao
    private var progressDialog: ProgressDialog? = null
    private var progressDialogMessageFormat: String? = null
    var fileFormatFilter: FileFormatFilter = FileFormatFilter.NONE

    init {
        this.placemarkDao = PlacemarkDao(context)
        this.placemarkCollectionDao = PlacemarkCollectionDao(context)
    }

    /**
     * Set optional progress dialog to show and update with progress

     * @see .setProgressDialogMessageFormat
     */
    fun setProgressDialog(progressDialog: ProgressDialog) {
        this.progressDialog = progressDialog
    }

    fun setProgressDialogMessageFormat(progressDialogMessageFormat: String) {
        this.progressDialogMessageFormat = progressDialogMessageFormat
    }

    /**
     * Import a generic resource into data base, this action refresh collection.
     * If imported count is 0 no modification is done.
     * **Side effect** update and save placermak collection
     * @return imported [io.github.fvasco.pinpoi.model.Placemark]
     */
    @Throws(IOException::class)
    fun importPlacemarks(placemarkCollection: PlacemarkCollection): Int {
        val resource = placemarkCollection.source
        val (mimeType, inputStream) = openInputStream(resource)
        val importer = createImporter(resource, mimeType, fileFormatFilter)
            ?: throw IOException("Cannot import $resource")
        return importPlacemarks(placemarkCollection, importer, inputStream)
    }

    fun importPlacemarks(
        placemarkCollection: PlacemarkCollection,
        importer: AbstractImporter,
        inputStream: InputStream
    ): Int {
        placemarkCollectionDao.open()
        val placemarkQueue = ArrayBlockingQueue<PlacemarkEvent>(256)
        try {
            progressDialog?.also { pd ->
                runOnUiThread {
                    pd.show()
                }
            }
            // insert new placemarks
            val importFuture = doAsync {
                try {
                    inputStream.use { inputStream ->
                        try {
                            importer.collectionId = placemarkCollection.id
                            importer.consumer = { placemarkQueue.put(PlacemarkEvent.NewPlacemark(it)) }
                            importer.importPlacemarks(inputStream)
                        } catch (e: Exception) {
                            placemarkQueue.put(PlacemarkEvent.ParseError(e))
                        }
                    }
                } finally {
                    placemarkQueue.put(PlacemarkEvent.End)
                }
            }
            var placemarkCount = 0
            placemarkDao.open()
            val placemarkDaoDatabase = placemarkDao.database!!
            placemarkDaoDatabase.beginTransaction()
            try {
                // remove old placemarks
                placemarkDao.deleteByCollectionId(placemarkCollection.id)
                var placemarkEvent = placemarkQueue.take()
                while (placemarkEvent is PlacemarkEvent.NewPlacemark) {
                    val placemark = placemarkEvent.placemark
                    if (placemarkDao.insert(placemark)) {
                        ++placemarkCount
                    } else {
                        // discard (duplicate?) placemark
                        if (BuildConfig.DEBUG) {
                            Log.d(ImporterFacade::class.java.simpleName, "Placemark discarded $placemark")
                        }
                    }
                    placemarkEvent = placemarkQueue.take()
                }
                if (placemarkEvent is PlacemarkEvent.ParseError) throw placemarkEvent.throwable
                // wait import and check exception
                importFuture.get()
                if (placemarkCount > 0) {
                    // update placemark collection
                    placemarkCollection.lastUpdate = System.currentTimeMillis()
                    placemarkCollection.poiCount = placemarkCount
                    placemarkCollectionDao.update(placemarkCollection)
                    // confirm transaction
                    placemarkDaoDatabase.setTransactionSuccessful()
                }
                return placemarkCount
            } catch (e: Exception) {
                throw IOException("Error importing placemarks", e)
            } finally {
                importFuture.cancel(true)
                placemarkDaoDatabase.endTransaction()
                placemarkDao.close()
            }
        } finally {
            try {
                placemarkCollectionDao.close()
            } finally {
                if (progressDialog != null) runOnUiThread { progressDialog?.tryDismiss() }
            }
        }
    }

    companion object {
        internal fun createImporter(
            resource: String,
            mimeType: String?,
            fileFormatFilter: FileFormatFilter
        ): AbstractImporter? {
            var res: AbstractImporter? = null

            if (mimeType != null) res = createImporterFromMimeType(mimeType, fileFormatFilter)

            if (res == null) {
                val path = try {
                    if (resource.isUri()) Uri.parse(resource).pathSegments.lastOrNull() ?: resource
                    else resource
                } catch (e: Exception) {
                    resource
                }

                when (path.substringAfterLast('.').lowercase()) {
                    in FileFormatFilter.KML.validExtensions -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.KML) res =
                        KmlImporter()
                    "kmz", "zip" -> res = ZipImporter()
                    "xml", "rss" -> if (fileFormatFilter == FileFormatFilter.NONE) res = GeoRssImporter()
                    in FileFormatFilter.GPX.validExtensions -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.GPX) res =
                        GpxImporter()
                    in FileFormatFilter.OV2.validExtensions -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.OV2) res =
                        Ov2Importer()
                    in FileFormatFilter.CSV_LAT_LON.validExtensions -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.CSV_LAT_LON) res =
                        TextImporter()
                    in FileFormatFilter.CSV_LON_LAT.validExtensions -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.CSV_LON_LAT) res =
                        TextImporter()
                }
            }

            if (res == null) res = fileFormatFilter.toAbstractImporter()

            res?.fileFormatFilter = fileFormatFilter
            Log.d(
                ImporterFacade::class.java.simpleName,
                "Importer for " + resource + " is " + res?.javaClass?.simpleName
            )
            return res
        }

        fun createImporterFromMimeType(mimeType: String, fileFormatFilter: FileFormatFilter): AbstractImporter? {
            if (mimeType.startsWith("text/")) return FileFormatFilter.CSV_LAT_LON.toAbstractImporter()

            val type = mimeType.substringAfterLast('/').substringAfterLast('.').substringBefore('+')
            when (type) {
                "kmz", "zip", "zip-compressed", "x-zip-compressed" -> return ZipImporter()
                "xml", "rss" -> return GeoRssImporter()
            }

            return (FileFormatFilter.values().find { type in it.validExtensions } ?: fileFormatFilter)
                .toAbstractImporter()
        }

        private fun FileFormatFilter.toAbstractImporter(): AbstractImporter? =
            when (this) {
                FileFormatFilter.NONE -> null
                FileFormatFilter.CSV_LAT_LON, FileFormatFilter.CSV_LON_LAT -> TextImporter()
                FileFormatFilter.GPX -> GpxImporter()
                FileFormatFilter.KML -> KmlImporter()
                FileFormatFilter.OV2 -> Ov2Importer()
            }

        private fun openInputStream(resource: String): Source {
            val connection = makeURL(resource).openConnection()
            connection.connect()
            val mimeType: String? = connection.getHeaderField("Content-Type")
            val inputStream: InputStream = connection.getInputStream()
            return Source(mimeType, inputStream)
        }

        private data class Source(val mimeType: String?, val inputStream: InputStream)

        private sealed class PlacemarkEvent {
            class NewPlacemark(val placemark: Placemark) : PlacemarkEvent()
            object End : PlacemarkEvent()
            class ParseError(val throwable: Throwable) : PlacemarkEvent()
        }
    }
}
