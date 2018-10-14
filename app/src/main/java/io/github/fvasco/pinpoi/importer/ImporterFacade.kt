package io.github.fvasco.pinpoi.importer

import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.fvasco.pinpoi.BuildConfig
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.ProgressDialogInputStream
import io.github.fvasco.pinpoi.util.isUri
import io.github.fvasco.pinpoi.util.tryDismiss
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.uiThread
import java.io.*
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import kotlin.apply

/**
 * Importer facade for:
 *
 *  * KML
 *  * KMZ
 *  * GPX
 *  * RSS
 *  * OV2 Tomtom
 *  * ASC, CSV files
 *
 *
 *
 * Import [Placemark] and update [PlacemarkCollectionDao]

 * @author Francesco Vasco
 */
class ImporterFacade(private val context: Context) {

    private val placemarkDao: PlacemarkDao
    private val placemarkCollectionDao: PlacemarkCollectionDao
    private val placemarkQueue = ArrayBlockingQueue<PlacemarkEvent>(256)
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
        val importer = createImporter(resource, fileFormatFilter) ?: throw IOException("Cannot import $resource")
        placemarkCollectionDao.open()
        try {
            progressDialog?.apply {
                setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                context.runOnUiThread { this@apply.show() }
            }
            // insert new placemark
            val importFuture = doAsync {
                try {
                    val max: Int
                    var inputStream: InputStream = if (resource.startsWith("/")) {
                        val file = File(resource)
                        max = file.length().toInt()
                        BufferedInputStream(FileInputStream(file))
                    } else {
                        val urlConnection = URL(resource).openConnection()
                        max = urlConnection.contentLength
                        urlConnection.inputStream
                    }
                    try {
                        progressDialog?.apply {
                            if (max > 0) {
                                this.max = max
                                inputStream = ProgressDialogInputStream(inputStream, this)
                            } else {
                                uiThread {
                                    isIndeterminate = true
                                }
                            }
                        }

                        importer.collectionId = placemarkCollection.id
                        importer.consumer = { placemarkQueue.put(PlacemarkEvent.NewPlacemark(it)) }
                        importer.importPlacemarks(inputStream)
                    } catch (e: Exception) {
                        placemarkQueue.put(PlacemarkEvent.ParseError(e))
                    } finally {
                        inputStream.close()
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
                // remove old placemark
                placemarkDao.deleteByCollectionId(placemarkCollection.id)
                var placemarkEvent = placemarkQueue.take()
                while (placemarkEvent is PlacemarkEvent.NewPlacemark) {
                    val placemark = placemarkEvent.placemark
                    if (placemarkDao.insert(placemark)) {
                        ++placemarkCount
                        if (progressDialog != null && progressDialogMessageFormat != null) {
                            val message = String.format(progressDialogMessageFormat!!, placemarkCount)
                            context.runOnUiThread { progressDialog?.setMessage(message) }
                        }
                    } else {
                        // discard (duplicate?) placemark
                        if (BuildConfig.DEBUG) {
                            Log.d(ImporterFacade::class.java.simpleName, "Placemark discarded $placemark")
                        }
                    }
                    placemarkEvent = placemarkQueue.take()
                }
                if (placemarkEvent is PlacemarkEvent.ParseError) throw placemarkEvent.throwable
                progressDialog?.let {
                    context.runOnUiThread { it.isIndeterminate = true }
                }
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
            } catch (e: InterruptedException) {
                throw IOException("Error importing placemarks", e)
            } catch (e: RuntimeException) {
                throw IOException("Error importing placemarks", e)
            } catch (e: ExecutionException) {
                throw IOException("Error importing placemarks", e.cause)
            } finally {
                importFuture.cancel(true)
                placemarkDaoDatabase.endTransaction()
                placemarkDao.close()
            }
        } finally {
            try {
                placemarkCollectionDao.close()
            } finally {
                if (progressDialog != null) context.runOnUiThread { progressDialog?.tryDismiss() }
            }
        }
    }

    companion object {
        internal fun createImporter(resource: String, fileFormatFilter: FileFormatFilter): AbstractImporter? {
            var path: String?
            try {
                if (resource.isUri()) {
                    val segments = Uri.parse(resource).pathSegments
                    path = null
                    var i = segments.size - 1
                    while (i >= 0 && path.isNullOrEmpty()) {
                        path = segments[i]
                        --i
                    }
                } else {
                    path = resource
                }
            } catch (e: Exception) {
                path = resource
            }
            if (path == null) return null

            var res: AbstractImporter? = null
            if (path.length >= 3) {
                val end = path.substringAfterLast('.').toLowerCase()
                when (end) {
                    in FileFormatFilter.KML.validExtension -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.KML) res = KmlImporter()
                    "kmz", "zip" -> res = ZipImporter()
                    "xml", "rss" -> if (fileFormatFilter == FileFormatFilter.NONE) res = GeoRssImporter()
                    in FileFormatFilter.GPX.validExtension -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.GPX) res = GpxImporter()
                    in FileFormatFilter.OV2.validExtension -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.OV2) res = Ov2Importer()
                    in FileFormatFilter.CSV_LAT_LON.validExtension -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.CSV_LAT_LON) res = TextImporter()
                    in FileFormatFilter.CSV_LON_LAT.validExtension -> if (fileFormatFilter == FileFormatFilter.NONE || fileFormatFilter == FileFormatFilter.CSV_LON_LAT) res = TextImporter()
                }
            }
            if (res == null) {
                res = when (fileFormatFilter) {
                    FileFormatFilter.NONE -> null
                    FileFormatFilter.CSV_LAT_LON, FileFormatFilter.CSV_LON_LAT -> TextImporter()
                    FileFormatFilter.GPX -> GpxImporter()
                    FileFormatFilter.KML -> KmlImporter()
                    FileFormatFilter.OV2 -> Ov2Importer()
                }
            }
            res?.fileFormatFilter = fileFormatFilter
            Log.d(ImporterFacade::class.java.simpleName,
                    "Importer for " + resource + " is " + if (res == null) null else res.javaClass.simpleName)
            return res
        }
    }

    private sealed class PlacemarkEvent {
        class NewPlacemark(val placemark: Placemark) : PlacemarkEvent()
        object End : PlacemarkEvent()
        class ParseError(val throwable: Throwable) : PlacemarkEvent()
    }
}
