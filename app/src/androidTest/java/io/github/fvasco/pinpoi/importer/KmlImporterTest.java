package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class KmlImporterTest extends AbstractImporterTestCase {

    @Test
    public void testImport2() throws Exception {
        final List<Placemark> list = importPlacemark(new KmlImporter(), "test2.kml");
        assertEquals(2, list.size());
        Placemark p = list.get(0);
        assertEquals("New York City", p.getName());
        assertEquals("New York City description", p.getDescription());
        assertEquals(-74.006393, p.getLongitude(), 0.1);
        assertEquals(40.714172, p.getLatitude(), 0.1);
        p = list.get(1);
        assertEquals("The Pentagon", p.getName());
        assertEquals(null, p.getDescription());
        assertEquals(-77.056, p.getLongitude(), 0.1);
        assertEquals(38.871, p.getLatitude(), 0.1);
    }

    @Test
    public void testImportNetworkLink() throws Exception {
        final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<kml xmlns=\"http://earth.google.com/kml/2.0\">\n" +
                "    <Document>\n" +
                "        <name>Test</name>\n" +
                "        <NetworkLink>\n" +
                "            <Url>\n" +
                "                <href>" + KmlImporterTest.class.getResource("test2.kml") + "</href>\n" +
                "            </Url>\n" +
                "        </NetworkLink>\n" +
                "    </Document>\n" +
                "</kml>";
        final List<Placemark> list = importPlacemark(new KmlImporter(), new ByteArrayInputStream(xml.getBytes("utf-8")));
        assertEquals(2, list.size());
    }
}