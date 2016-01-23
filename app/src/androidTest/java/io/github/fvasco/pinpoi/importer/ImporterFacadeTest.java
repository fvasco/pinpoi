package io.github.fvasco.pinpoi.importer;

import android.content.Context;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.io.IOException;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class ImporterFacadeTest extends AbstractImporterTestCase {

    @Test
    public void testImportPlacemarksKml() throws Exception {
        final Context context = new RenamingDelegatingContext(getContext(), "test_");
        final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(context);
        final PlacemarkDao placemarkDao = new PlacemarkDao(context);
        placemarkCollectionDao.open();
        placemarkDao.open();

        PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName("test");
        pc.setDescription("description");
        pc.setSource(getClass().getResource("test2.kml").toString());
        pc.setCategory("category");
        pc.setLastUpdate(5);
        pc.setPoiCount(5);
        placemarkCollectionDao.insert(pc);

        final ImporterFacade importerFacade = new ImporterFacade(context);
        int count = importerFacade.importPlacemarks(1);
        assertEquals(2, count);


        pc = placemarkCollectionDao.findPlacemarkCollectionById(1);
        assertEquals(count, pc.getPoiCount());
        final long lastUpdate = pc.getLastUpdate();
        assertTrue(lastUpdate > 5);

        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(1).size());

        pc.setSource("wrong");
        placemarkCollectionDao.update(pc);
        try {
            importerFacade.importPlacemarks(1);
            fail();
        } catch (final IOException e) {
            // ok
        }
        pc = placemarkCollectionDao.findPlacemarkCollectionById(1);
        assertEquals(count, pc.getPoiCount());
        assertEquals(lastUpdate, pc.getLastUpdate());
        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(1).size());

        placemarkDao.close();
        placemarkCollectionDao.close();
    }
}