package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class GpxImporterTest extends AbstractImporterTestCase {

    @Test
    public void testImportImpl() throws Exception {
        final List<Placemark> list = importPlacemark(new GpxImporter(), "test.gpx");

        assertEquals(2, list.size());

        final Placemark p = list.get(0);
        assertEquals("Test", p.getName());
        assertEquals("descTest", p.getDescription());
        assertEquals(1, p.getLatitude(), 0.1);
        assertEquals(2, p.getLongitude(), 0.1);
    }
}