package io.github.fvasco.pinpoi.importer

import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
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