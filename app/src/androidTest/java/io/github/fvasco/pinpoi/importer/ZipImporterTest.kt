package io.github.fvasco.pinpoi.importer

import org.junit.Test

/**
 * @author Francesco Vasco
 */
class ZipImporterTest : AbstractImporterTestCase() {

    @Test
    @Throws(Exception::class)
    fun testImportImpl() {
        val list = importPlacemark(ZipImporter(), "test3.kmz")
        assertEquals(3, list.size)
    }
}