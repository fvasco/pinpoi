package io.github.fvasco.pinpoi.importer;

import android.test.RenamingDelegatingContext;

import org.junit.Test;

/**
 * @author Francesco Vasco
 */
public class ImporterFacadeTest extends AbstractImporterTestCase {

    @Test
    public void testImportPlacemarksKml() throws Exception {
        final ImporterFacade importerFacade = new ImporterFacade(new RenamingDelegatingContext(getContext(), "test_"));
        int count = importerFacade.importPlacemarks(getClass().getResource("test1.kml").getFile(), 1);
        assertEquals(1, count);
    }
}