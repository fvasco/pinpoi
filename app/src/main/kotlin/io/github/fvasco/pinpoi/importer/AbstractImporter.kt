package io.github.fvasco.pinpoi.importer

import android.util.Log
import io.github.fvasco.pinpoi.BuildConfig
import io.github.fvasco.pinpoi.model.Placemark
import java.io.IOException
import java.io.InputStream

/**
 * Abstract base importer.

 * @author Francesco Vasco
 */
abstract class AbstractImporter {
    var consumer: ((Placemark) -> Unit)? = null
    var collectionId: Long = 0

    /**
     * Import data

     * @param inputStream data source
     * *
     * @throws IOException error during reading
     */
    @Throws(IOException::class)
    fun importPlacemarks(inputStream: InputStream) {
        if (consumer == null) error("No consumer")
        if (collectionId <= 0) {
            error("Collection id not valid: $collectionId")
        }
        // do import
        importImpl(inputStream)
    }

    protected fun importPlacemark(placemark: Placemark) {
        val (latitude, longitude) = placemark.coordinates
        if ( latitude >= -90f && latitude <= 90f
                && longitude >= -180f && longitude <= 180f) {
            val name = placemark.name.trim()
            var description: String = placemark.description.trim()
            if (description == name) description = ""
            placemark.name = name
            placemark.description = description
            placemark.collectionId = collectionId
            if (BuildConfig.DEBUG) {
                Log.d(AbstractImporter::class.java.simpleName, "importPlacemark " + placemark)
            }
            consumer!!(placemark)
        } else if (BuildConfig.DEBUG) {
            Log.d(AbstractImporter::class.java.simpleName, "importPlacemark skip " + placemark)
        }
    }

    /**
     * Configure importer from another
     */
    protected fun configureFrom(importer: AbstractImporter) {
        collectionId = importer.collectionId
        consumer = importer.consumer
    }

    /**
     * Read datas, use [.importPlacemark] to persistence it

     * @param inputStream data source
     * *
     * @throws IOException error during reading
     */
    @Throws(IOException::class)
    protected abstract fun importImpl(inputStream: InputStream)
}
