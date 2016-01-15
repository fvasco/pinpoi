package io.github.fvasco.pinpoi.dao;

import android.location.Location;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class PlacemarkDaoTest extends AndroidTestCase {
    private PlacemarkDao dao;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RenamingDelegatingContext context = new RenamingDelegatingContext(getContext(), "test_");
        dao = new PlacemarkDao(context);
        dao.open();
    }

    @Override
    protected void tearDown() throws Exception {
        dao.close();
        dao = null;
        super.tearDown();
    }


    @Test
    public void testGetAllPlacemarkNear() throws Exception {
        Placemark p = new Placemark();
        p.setName("Pompei");
        p.setLatitude(40.7491819F);
        p.setLongitude(14.5007385F);
        p.setCollectionId(1);
        dao.insert(p);

        p = new Placemark();
        p.setName("Ercolano");
        p.setLatitude(40.8060768f);
        p.setLongitude(14.3529209f);
        p.setCollectionId(1);
        dao.insert(p);

        // Pompei
        Location lp = new Location(PlacemarkDaoTest.class.getSimpleName());
        lp.setLatitude(40.7491819F);
        lp.setLongitude(14.5007385F);
        // Ercolano
        Location le = new Location(PlacemarkDaoTest.class.getSimpleName());
        le.setLatitude(40.8060768F);
        le.setLongitude(14.3529209F);
        // Vesuvio
        Location lv = new Location(PlacemarkDaoTest.class.getSimpleName());
        lv.setLatitude(40.816667F);
        lv.setLongitude(14.433333F);


        SortedSet<Placemark> set = dao.getAllPlacemarkNear(lp, 1, Arrays.asList(1L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());

        // empty catalog
        set = dao.getAllPlacemarkNear(lp, 1, Arrays.asList(2L));
        assertTrue(set.isEmpty());

        // no poi near vesuvio
        set = dao.getAllPlacemarkNear(lv, 1000, Arrays.asList(1L));
        assertTrue(set.isEmpty());

        // only Pompei
        set = dao.getAllPlacemarkNear(lp, 12000, Arrays.asList(0L, 1L, 2L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());

        // all data, Pompei first
        set = dao.getAllPlacemarkNear(lp, 14000, Arrays.asList(1L));
        assertEquals(2, set.size());
        Iterator<Placemark> iterator = set.iterator();
        assertEquals("Pompei", iterator.next().getName());
        assertEquals("Ercolano", iterator.next().getName());
    }

    @Test
    public void testInsert() throws Exception {
        Placemark p = new Placemark();
        p.setName("Pompei");
        p.setDescription("Pompei city");
        p.setLatitude(40.7491819F);
        p.setLongitude(14.5007385F);
        p.setCollectionId(1);
        dao.insert(p);

        final List<Placemark> list = dao.getAllPlacemarkByCollectionId(1);
        assertEquals(1, list.size());
        p = list.get(0);
        assertEquals("Pompei", p.getName());
        assertEquals("Pompei city", p.getDescription());
        assertEquals(40.7491819F, p.getLatitude(), 0.00001);
        assertEquals(14.5007385F, p.getLongitude(), 0.00001);
        assertEquals(1, p.getCollectionId());
    }

    @Test
    public void testDeleteByCollectionId() throws Exception {
        Placemark p = new Placemark();
        p.setName("test1");
        p.setDescription("description1");
        p.setLatitude(3);
        p.setLongitude(2);
        p.setCollectionId(1);
        dao.insert(p);

        dao.deleteByCollectionId(999);
        List<Placemark> list = dao.getAllPlacemarkByCollectionId(1);
        assertEquals(1, list.size());

        dao.deleteByCollectionId(1);
        list = dao.getAllPlacemarkByCollectionId(1);
        assertTrue(list.isEmpty());

    }
}