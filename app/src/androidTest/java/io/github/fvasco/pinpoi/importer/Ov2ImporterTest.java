package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class Ov2ImporterTest extends AbstractImporterTestCase {

    @Test
    public void testImportImpl() throws Exception {
        List<Placemark> list = importPlacemark(new Ov2Importer(), "test.ov2");
        assertEquals(2, list.size());

        Placemark p = list.get(0);
        assertEquals("New York City", p.getName());
        assertEquals(-74.006393, p.getLongitude(), 0.1);
        assertEquals(40.714172, p.getLatitude(), 0.1);
        p = list.get(1);
        assertEquals("This is the location of my office.", p.getName());
        assertEquals(-122.087461, p.getLongitude(), 0.1);
        assertEquals(37.422069, p.getLatitude(), 0.1);
    }
}