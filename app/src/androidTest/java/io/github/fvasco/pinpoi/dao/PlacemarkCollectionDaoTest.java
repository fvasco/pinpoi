package io.github.fvasco.pinpoi.dao;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.util.Date;
import java.util.List;

import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class PlacemarkCollectionDaoTest extends AndroidTestCase {

    private PlacemarkCollectionDao dao;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
        dao = new PlacemarkCollectionDao(context);
        dao.open();
    }

    @Override
    protected void tearDown() throws Exception {
        dao.close();
        dao = null;
        super.tearDown();
    }

    @Test
    public void testFindPlacemarkCollectionCategory() throws Exception {
        PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName("test");
        pc.setDescription("description");
        pc.setSource("source");
        pc.setCategory("category");
        pc.setLastUpdate(new Date());
        dao.insert(pc);

        List<String> list = dao.findAllPlacemarkCollectionCategory();
        assertEquals(1, list.size());
        String category = list.get(0);
        assertEquals("category", category);
    }

    @Test
    public void testFindPlacemarkCollection() throws Exception {
        PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName("test");
        pc.setDescription("description");
        pc.setSource("source");
        pc.setCategory("category");
        pc.setLastUpdate(new Date(5));
        pc.setPoiCount(5);
        dao.insert(pc);

        // check findPlacemarkCollectionById
        pc = dao.findPlacemarkCollectionById(1);
        assertEquals(1, pc.getId());
        assertEquals("test", pc.getName());
        assertEquals("description", pc.getDescription());
        assertEquals("source", pc.getSource());
        assertEquals("category", pc.getCategory());
        assertEquals(new Date(5), pc.getLastUpdate());
        assertEquals(5, pc.getPoiCount());

        pc.setName("test2");
        pc.setDescription("description2");
        pc.setSource("source2");
        pc.setCategory("category2");
        pc.setLastUpdate(new Date(7));
        pc.setPoiCount(7);
        dao.update(pc);

        // check findAllPlacemarkCollection
        List<PlacemarkCollection> list = dao.findAllPlacemarkCollection();
        assertEquals(1, list.size());
        pc = list.get(0);
        assertEquals(1, pc.getId());
        assertEquals("test2", pc.getName());
        assertEquals("description2", pc.getDescription());
        assertEquals("source2", pc.getSource());
        assertEquals("category2", pc.getCategory());
        assertEquals(new Date(7), pc.getLastUpdate());
        assertEquals(7, pc.getPoiCount());

        dao.delete(pc);
        list = dao.findAllPlacemarkCollection();
        assertTrue(list.isEmpty());
        assertNull(dao.findPlacemarkCollectionById(1));
    }
}