package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class GeoRssImporterTest extends AbstractImporterTestCase {


    @Test
    public void testimportAtom() throws Exception {
        final List<Placemark> list = importPlacemark(new GeoRssImporter(), "georss.atom.xml");

        assertEquals(1, list.size());

        final Placemark p = list.get(0);
        assertEquals("M 3.2, Mona Passage", p.getName());
        assertEquals("We just had a big one.", p.getDescription());
        assertEquals(45.256F, p.getLatitude(), 0.1);
        assertEquals(-71.92F, p.getLongitude(), 0.1);
    }

    @Test
    public void testimportRss() throws Exception {
        final List<Placemark> list = importPlacemark(new GeoRssImporter(), "georss.rss.xml");

        assertEquals(1, list.size());

        final Placemark p = list.get(0);
        assertEquals("M 5.3, northern Sumatra, Indonesia", p.getName());
        assertEquals("December 28, 2007 05:24:17 GMT", p.getDescription());
        assertEquals(5.5319F, p.getLatitude(), 0.1);
        assertEquals(95.8972F, p.getLongitude(), 0.1);
    }
}