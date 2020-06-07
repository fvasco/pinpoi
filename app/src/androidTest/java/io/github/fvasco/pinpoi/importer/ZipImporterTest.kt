package io.github.fvasco.pinpoi.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
class ZipImporterTest : AbstractImporterTestCase() {

    @Test
    @Throws(Exception::class)
    fun testImportImpl() {
        val list = importPlacemark(ZipImporter(), "test3.kmz")
        assertEquals(3, list.size)
    }
}