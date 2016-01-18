package io.github.fvasco.pinpoi.importer;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class TextImporterTest extends AbstractImporterTestCase {
    @Test
    public void testImportImplAsc() throws Exception {
        final List<Placemark> list = importPlacemark(new TextImporter(), "asc.txt");
        assertEquals(2, list.size());
    }

    @Test
    public void testImportImplCsv() throws Exception {
        final List<Placemark> list = importPlacemark(new TextImporter(), "csv.txt");
        assertEquals(2, list.size());
    }
}