package io.github.fvasco.pinpoi.importer;

import android.content.Context;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.util.Date;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class ImporterFacadeTest extends AbstractImporterTestCase {

    @Test
    public void testImportPlacemarksKml() throws Exception {
        final Context context = new RenamingDelegatingContext(getContext(), "test_");
        final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(context);
        placemarkCollectionDao.open();
        PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName("test");
        pc.setDescription("description");
        pc.setSource("source");
        pc.setCategory("category");
        pc.setLastUpdate(new Date(5));
        pc.setPoiCount(5);
        placemarkCollectionDao.insert(pc);

        final ImporterFacade importerFacade = new ImporterFacade(context);
        int count = importerFacade.importPlacemarks(getClass().getResource("test1.kml").toString(), 1);
        assertEquals(1, count);


        pc = placemarkCollectionDao.findPlacemarkCollectionById(1);
        assertEquals(count, pc.getPoiCount());
        placemarkCollectionDao.close();
    }
}