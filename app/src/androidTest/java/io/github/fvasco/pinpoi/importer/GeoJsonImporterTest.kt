package io.github.fvasco.pinpoi.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
class GeoJsonImporterTest : AbstractImporterTestCase() {
    @Test
    fun testImportImplAsc() {
        val list = importPlacemark(GeoJsonImporter(), "test.geojson")
        assertEquals(3, list.size)
        var p = list[0]
        assertEquals("Place 0", p.name)
        assertEquals(1.1F, p.coordinates.longitude, 0.1F)
        assertEquals(2.1F, p.coordinates.latitude, 0.1F)
        p = list[1]
        assertEquals("Place 1", p.name)
        assertEquals(1.2F, p.coordinates.longitude, 0.1F)
        assertEquals(2.2F, p.coordinates.latitude, 0.1F)
        p = list[2]
        assertEquals("Place 2", p.name)
        assertEquals(1.3F, p.coordinates.longitude, 0.1F)
        assertEquals(2.3F, p.coordinates.latitude, 0.1F)
    }
}
