package io.github.fvasco.pinpoi.dao;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

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
        dao = new PlacemarkCollectionDao(new RenamingDelegatingContext(getContext(), "test_"));
        dao.open();
        for (final PlacemarkCollection pc : dao.findAllPlacemarkCollection()) {
            dao.delete(pc);
        }
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
        pc.setCategory("CATEGORY");
        pc.setLastUpdate(System.currentTimeMillis());
        dao.insert(pc);

        List<String> list = dao.findAllPlacemarkCollectionCategory();
        assertEquals(1, list.size());
        String category = list.get(0);
        assertEquals("CATEGORY", category);
    }

    @Test
    public void testFindPlacemarkCollection() throws Exception {
        PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName("test");
        pc.setDescription("description");
        pc.setSource("source");
        pc.setCategory("CATEGORY");
        pc.setLastUpdate(1);
        pc.setPoiCount(2);
        dao.insert(pc);

        final long id = pc.getId();
        // check findPlacemarkCollectionById
        pc = dao.findPlacemarkCollectionById(id);
        assertEquals(id, pc.getId());
        assertEquals("test", pc.getName());
        assertEquals("description", pc.getDescription());
        assertEquals("source", pc.getSource());
        assertEquals("CATEGORY", pc.getCategory());
        assertEquals(1, pc.getLastUpdate());
        assertEquals(2, pc.getPoiCount());

        pc.setName("test2");
        pc.setDescription("description2");
        pc.setSource("source2");
        pc.setCategory("CATEGORY2");
        pc.setLastUpdate(5);
        pc.setPoiCount(6);
        dao.update(pc);

        // check findAllPlacemarkCollection
        List<PlacemarkCollection> list = dao.findAllPlacemarkCollection();
        assertEquals(1, list.size());
        pc = list.get(0);
        assertEquals(id, pc.getId());
        assertEquals("test2", pc.getName());
        assertEquals("description2", pc.getDescription());
        assertEquals("source2", pc.getSource());
        assertEquals("CATEGORY2", pc.getCategory());
        assertEquals(5, pc.getLastUpdate());
        assertEquals(6, pc.getPoiCount());

        dao.delete(pc);
        list = dao.findAllPlacemarkCollection();
        assertTrue(list.isEmpty());
        assertNull(dao.findPlacemarkCollectionById(1));
    }

    public void testFindPlacemarkCollectionByName() throws Exception {
        PlacemarkCollection pc = new PlacemarkCollection();
        pc.setName("test");
        pc.setDescription("description");
        pc.setSource("source");
        pc.setCategory("CATEGORY");
        pc.setLastUpdate(5);
        pc.setPoiCount(6);
        dao.insert(pc);

        final long id = pc.getId();
        // check findPlacemarkCollectionByName
        pc = dao.findPlacemarkCollectionByName("test");
        assertEquals(id, pc.getId());
        assertEquals("test", pc.getName());
        assertEquals("description", pc.getDescription());
        assertEquals("source", pc.getSource());
        assertEquals("CATEGORY", pc.getCategory());
        assertEquals(5, pc.getLastUpdate());
        assertEquals(6, pc.getPoiCount());
    }
}