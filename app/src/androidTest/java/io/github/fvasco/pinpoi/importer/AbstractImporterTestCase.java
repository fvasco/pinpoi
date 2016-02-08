package io.github.fvasco.pinpoi.importer;

import android.test.AndroidTestCase;

import java.io.InputStream;
import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Base importer test case class.
 *
 * @autor Francesco Vasco
 */
public abstract class AbstractImporterTestCase extends AndroidTestCase {

    /**
     * Execute import
     *
     * @param importer
     * @param resource resource file name
     * @return list of imported placemark
     * @throws Exception
     */
    public List<Placemark> importPlacemark(final AbstractImporter importer, final String resource) throws Exception {
        final ListConsumer<Placemark> list = new ListConsumer<>();
        importer.setCollectionId(1);
        importer.setConsumer(list);
        int count;
        try (final InputStream is = getClass().getResourceAsStream(resource)) {
            count = importer.importPlacemarks(is);
        }
        assertEquals(list.size(), count);
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
