package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

import static org.junit.Assert.assertEquals;

/**
 * @author Francesco Vasco
 */
public class KmzImporterTest extends AbstractImporterTestCase {

    @Test
    public void testImportImpl() throws Exception {
        final List<Placemark> list = importPlacemark(new KmzImporter(), "test3.kmz");
        assertEquals(3, list.size());
    }
}