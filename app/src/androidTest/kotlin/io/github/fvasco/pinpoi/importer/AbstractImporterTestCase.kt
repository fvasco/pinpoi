package io.github.fvasco.pinpoi.importer

import android.test.AndroidTestCase
import io.github.fvasco.pinpoi.model.Placemark
import java.io.InputStream
import java.util.*

/**
 * Base importer test case class.

 * @author Francesco Vasco
 */
abstract class AbstractImporterTestCase : AndroidTestCase() {

    /**
     * Execute import

     * @param resource resource file name
     * *
     * @return list of imported placemark
     */
    @Throws(Exception::class)
    fun importPlacemark(importer: AbstractImporter, resource: String): List<Placemark> {
        javaClass.getResourceAsStream(resource).use {
            return importPlacemark(importer, it)
        }
    }

    /**
     * Execute import
     */
    @Throws(Exception::class)
    fun importPlacemark(importer: AbstractImporter, input: InputStream): List<Placemark> {
        val list = ArrayList<Placemark>()
        importer.collectionId = 1
        importer.consumer = { list.add(it) }
        importer.importPlacemarks(input)
        for (p in list) {
            assertEquals(0, p.id)
            assertTrue(!p.name.isEmpty())
            assertTrue(!java.lang.Float.isNaN(p.coordinates.latitude))
            assertTrue(!java.lang.Float.isNaN(p.coordinates.longitude))
            assertEquals(1, p.collectionId)
        }
        return list
    }
}
