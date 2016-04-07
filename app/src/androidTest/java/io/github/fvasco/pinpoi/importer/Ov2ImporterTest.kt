package io.github.fvasco.pinpoi.importer

import org.junit.Test

/**
 * @author Francesco Vasco
 */
class Ov2ImporterTest : AbstractImporterTestCase() {

    @Test
    fun testImportImpl() {
        val list = importPlacemark(Ov2Importer(), "test.ov2")
        assertEquals(2, list.size)

        var p = list[0]
        assertEquals("New York City", p.name)
        assertEquals(40.714172, p.coordinates.latitude.toDouble(), 0.1)
        assertEquals(-74.006393, p.coordinates.longitude.toDouble(), 0.1)
        p = list[1]
        assertEquals("This is the location of my office.", p.name)
        assertEquals(37.422069, p.coordinates.latitude.toDouble(), 0.1)
        assertEquals(-122.087461, p.coordinates.longitude.toDouble(), 0.1)
    }
}