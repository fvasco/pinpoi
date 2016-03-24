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
import io.github.fvasco.pinpoi.util.Util
import io.github.fvasco.pinpoi.util.isUri
import java.io.*
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException

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
class ImporterFacade constructor(context: Context = Util.applicationContext) {

    /**
     * Signpost for end of elaboration
     */
    private val STOP_PLACEMARK = Placemark()
    private val placemarkDao: PlacemarkDao
    private val placemarkCollectionDao: PlacemarkCollectionDao
    private val placemarkQueue = ArrayBlockingQueue<Placemark>(256)
    private var progressDialog: ProgressDialog? = null
    private var progressDialogMessageFormat: String? = null

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
        placemarkCollectionDao.open()
        try {
            val importer = createImporter(resource) ?: throw IOException("Cannot import $resource")

            // insert new placemark
            val importFuture = Util.EXECUTOR.submit({
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
                progressDialog?.apply {
                    isIndeterminate = max <= 0
                    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                    if (max > 0) {
                        this.max = max
                        inputStream = ProgressDialogInputStream(inputStream, this)
                    }
                    Util.MAIN_LOOPER_HANDLER.post { progressDialog!!.show() }
                }

                try {
                    importer.collectionId = placemarkCollection.id
                    importer.consumer = { placemarkQueue.put(it) }
                    importer.importPlacemarks(inputStream)
                } finally {
                    placemarkQueue.put(STOP_PLACEMARK)
                    inputStream.close()
                }
            })
            var placemarkCount = 0
            placemarkDao.open()
            val placemarkDaoDatabase = placemarkDao.database!!
            placemarkDaoDatabase.beginTransaction()
            try {
                // remove old placemark
                placemarkDao.deleteByCollectionId(placemarkCollection.id)
                var placemark = placemarkQueue.take()
                while (placemark !== STOP_PLACEMARK) {
                    if (placemarkDao.insert(placemark)) {
                        ++placemarkCount
                        if (progressDialog != null && progressDialogMessageFormat != null) {
                            val message = String.format(progressDialogMessageFormat!!, placemarkCount)
                            Util.MAIN_LOOPER_HANDLER.post { progressDialog!!.setMessage(message) }
                        }
                    } else {
                        // discard (duplicate?) placemark
                        if (BuildConfig.DEBUG) {
                            Log.i(ImporterFacade::class.java.simpleName, "Placemark discarded $placemark")
                        }
                    }
                    placemark = placemarkQueue.take()
                }
                if (progressDialog != null) {
                    Util.MAIN_LOOPER_HANDLER.post { progressDialog!!.isIndeterminate = true }
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
                throw IOException("Error importing placemark", e)
            } catch (e: RuntimeException) {
                throw IOException("Error importing placemark", e)
            } catch (e: ExecutionException) {
                throw IOException("Error importing placemark", e.cause)
            } finally {
                importFuture.cancel(true)
                placemarkDaoDatabase.endTransaction()
                placemarkDao.close()
            }
        } finally {
            placemarkCollectionDao.close()
            progressDialog?.let { pd ->
                Util.MAIN_LOOPER_HANDLER.post { pd.dismiss() }
            }
        }
    }

    companion object {

        internal fun createImporter(resource: String): AbstractImporter? {
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

            val res: AbstractImporter?
            if (path == null || path.length < 3) {
                res = null
            } else {
                val end = path.substring(path.length - 3)
                when (end.toLowerCase()) {
                    "kml" -> res = KmlImporter()
                    "kmz", "zip" -> res = ZipImporter()
                    "xml", "rss" -> res = GeoRssImporter()
                    "gpx" -> res = GpxImporter()
                    "ov2" -> res = Ov2Importer()
                    "asc", "csv", "txt" -> res = TextImporter()
                    else -> res = null
                }
            }
            Log.d(ImporterFacade::class.java.simpleName,
                    "Importer for " + resource + " is " + if (res == null) null else res.javaClass.simpleName)
            return res
        }
    }
}
