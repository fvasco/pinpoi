package io.github.fvasco.pinpoi.importer;

import android.content.Context;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class ImporterFacadeTest extends AbstractImporterTestCase {

    private PlacemarkCollectionDao placemarkCollectionDao;
    private PlacemarkDao placemarkDao;
    private Context context;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = new RenamingDelegatingContext(getContext(), "test_");
        placemarkCollectionDao = new PlacemarkCollectionDao(context);
        placemarkDao = new PlacemarkDao(context);
        placemarkCollectionDao.open();
        placemarkDao.open();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        placemarkDao.close();
        placemarkCollectionDao.close();
    }

    private PlacemarkCollection insertPlacemarkCollection(String resource) {
        final PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName(resource);
        final URL resourceUrl = getClass().getResource(resource);
        pc.setSource(resourceUrl == null ? resource : resourceUrl.toString());
        placemarkCollectionDao.insert(pc);
        return pc;
    }

    @Test
    public void testImportPlacemarksKml() throws Exception {
        final PlacemarkCollection pc = insertPlacemarkCollection("test2.kml");
        final ImporterFacade importerFacade = new ImporterFacade(context);
        int count = importerFacade.importPlacemarks(pc);
        assertEquals(2, count);

        assertEquals(count, pc.getPoiCount());
        final long lastUpdate = pc.getLastUpdate();
        assertTrue(lastUpdate > 0);

        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(pc.getId()).size());
    }

    @Test
    public void testImportPlacemarksWrong() throws Exception {
        final PlacemarkCollection pc = insertPlacemarkCollection("wrong");
        pc.setLastUpdate(1);
        pc.setPoiCount(10);
        final ImporterFacade importerFacade = new ImporterFacade(context);
        try {
            importerFacade.importPlacemarks(pc);
            fail();
        } catch (final IOException e) {
            // ok
        }
        assertEquals(1, pc.getLastUpdate());
        assertEquals(10, pc.getPoiCount());
    }

    @Test
    public void testImportPlacemarksArc() throws Exception {
        final PlacemarkCollection pc = insertPlacemarkCollection("asc.txt");
        final ImporterFacade importerFacade = new ImporterFacade(context);
        int count = importerFacade.importPlacemarks(pc);
        assertEquals(2, count);
        assertEquals(count, pc.getPoiCount());
        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(pc.getId()).size());
    }
}