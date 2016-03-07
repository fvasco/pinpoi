package io.github.fvasco.pinpoi.importer;

import android.test.AndroidTestCase;

import java.io.InputStream;
import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Base importer test case class.
 *
 * @author Francesco Vasco
 */
public abstract class AbstractImporterTestCase extends AndroidTestCase {

    /**
     * Execute import
     *
     * @param resource resource file name
     * @return list of imported placemark
     */
    public List<Placemark> importPlacemark(final AbstractImporter importer, final String resource) throws Exception {
        final InputStream is = getClass().getResourceAsStream(resource);
        try {
            return importPlacemark(importer, is);
        } finally {
            is.close();
        }
    }

    /**
     * Execute import
     */
    public List<Placemark> importPlacemark(final AbstractImporter importer, final InputStream is) throws Exception {
        final ListConsumer<Placemark> list = new ListConsumer<>();
        importer.setCollectionId(1);
        importer.setConsumer(list);
        importer.importPlacemarks(is);
        for (final Placemark p : list) {
            assertEquals(0, p.getId());
            assertNotNull(p.getName());
            assertTrue(!p.getName().isEmpty());
            assertTrue(!Float.isNaN(p.getLatitude()));
            assertTrue(!Float.isNaN(p.getLongitude()));
            assertEquals(1, p.getCollectionId());
        }
        return list;
    }
}
