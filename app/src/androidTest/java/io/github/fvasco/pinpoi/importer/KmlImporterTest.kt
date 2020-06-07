package io.github.fvasco.pinpoi.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
class KmlImporterTest : AbstractImporterTestCase() {

    @Test
    @Throws(Exception::class)
    fun testImport2() {
        val list = importPlacemark(KmlImporter(), "test2.kml")
        assertEquals(2, list.size)
        var p = list[0]
        assertEquals("New York City", p.name)
        assertEquals("New York City description", p.description)
        assertEquals(-74.006393, p.coordinates.longitude.toDouble(), 0.1)
        assertEquals(40.714172, p.coordinates.latitude.toDouble(), 0.1)
        p = list[1]
        assertEquals("The Pentagon", p.name)
        assertEquals("", p.description)
        assertEquals(-77.056, p.coordinates.longitude.toDouble(), 0.1)
        assertEquals(38.871, p.coordinates.latitude.toDouble(), 0.1)
    }

    @Test
    @Throws(Exception::class)
    fun testImportNetworkLink() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<kml xmlns=\"http://earth.google.com/kml/2.0\">\n" +
                "    <Document>\n" +
                "        <name>Test</name>\n" +
                "        <NetworkLink>\n" +
                "            <Url>\n" +
                "                <href>" + KmlImporterTest::class.java.getResource("test2.kml") + "</href>\n" +
                "            </Url>\n" +
                "        </NetworkLink>\n" +
                "    </Document>\n" +
                "</kml>"
        val list = importPlacemark(KmlImporter(), ByteArrayInputStream(xml.toByteArray(charset("utf-8"))))
        assertEquals(2, list.size)
    }
}