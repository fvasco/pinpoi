package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class ZipImporterTest extends AbstractImporterTestCase {

    @Test
    public void testImportImpl() throws Exception {
        final List<Placemark> list = importPlacemark(new ZipImporter(), "test3.kmz");
        assertEquals(3, list.size());
    }
}