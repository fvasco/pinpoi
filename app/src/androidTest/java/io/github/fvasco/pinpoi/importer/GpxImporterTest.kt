package io.github.fvasco.pinpoi.importer

import org.junit.Test

import io.github.fvasco.pinpoi.model.Placemark

/**
 * @author Francesco Vasco
 */
class GpxImporterTest : AbstractImporterTestCase() {

    @Test
    @Throws(Exception::class)
    fun testImportImpl() {
        val list = importPlacemark(GpxImporter(), "test.gpx")

        assertEquals(2, list.size)

        val p = list[0]
        assertEquals("Test", p.name)
        assertEquals("descTest", p.description)
        assertEquals(1.0, p.coordinates.latitude.toDouble(), 0.1)
        assertEquals(2.0, p.coordinates.longitude.toDouble(), 0.1)
    }
}