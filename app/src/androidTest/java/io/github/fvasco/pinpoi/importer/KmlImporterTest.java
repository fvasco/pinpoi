package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

import static org.junit.Assert.assertEquals;

/**
 * @author Francesco Vasco
 */
public class KmlImporterTest extends AbstractImporterTestCase {

    @Test
    public void testImportImpl() throws Exception {
        List<Placemark> list;
        list = importPlacemark(new KmlImporter(), "test1.kml");
        Placemark p = list.get(0);
        assertEquals("New York City", p.getName());
        assertEquals("New York City description", p.getDescription());
        assertEquals(-74.006393, p.getLongitude(), 0.1);
        assertEquals(40.714172, p.getLatitude(), 0.1);

        assertEquals(1, list.size());
        list = importPlacemark(new KmlImporter(), "test2.kml");
        assertEquals(2, list.size());
    }
}