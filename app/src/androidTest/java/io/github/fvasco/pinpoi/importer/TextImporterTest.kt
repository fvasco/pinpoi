package io.github.fvasco.pinpoi.importer

import org.junit.Test

import io.github.fvasco.pinpoi.model.Placemark

/**
 * @author Francesco Vasco
 */
class TextImporterTest : AbstractImporterTestCase() {
    @Test
    @Throws(Exception::class)
    fun testImportImplAsc() {
        val list = importPlacemark(TextImporter(), "asc.txt")
        assertEquals(3, list.size)
    }

    @Test
    @Throws(Exception::class)
    fun testImportImplCsv() {
        val list = importPlacemark(TextImporter(), "csv.txt")
        assertEquals(2, list.size)
    }
}