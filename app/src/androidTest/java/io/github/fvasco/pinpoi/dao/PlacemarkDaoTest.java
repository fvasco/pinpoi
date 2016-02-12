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
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.util.Util;

/**
 * @author Francesco Vasco
 */
public class PlacemarkDaoTest extends AndroidTestCase {
    public static final Location POMPEI_LOCATION;
    public static final Location ERCOLANO_LOCATION;
    public static final Location VESUVIO_LOCATION;

    static {
        // Pompei
        POMPEI_LOCATION = new Location(PlacemarkDaoTest.class.getSimpleName());
        POMPEI_LOCATION.setLatitude(40.7491819);
        POMPEI_LOCATION.setLongitude(14.5007385);
        // Ercolano
        ERCOLANO_LOCATION = new Location(PlacemarkDaoTest.class.getSimpleName());
        ERCOLANO_LOCATION.setLatitude(40.8060768);
        ERCOLANO_LOCATION.setLongitude(14.3529209);
        // Vesuvio
        VESUVIO_LOCATION = new Location(PlacemarkDaoTest.class.getSimpleName());
        VESUVIO_LOCATION.setLatitude(40.816667F);
        VESUVIO_LOCATION.setLongitude(14.433333F);
    }

    private PlacemarkDao dao;

    private static void assertEquals(double expected, double actual) {
        assertEquals(expected, actual, 0.00001);
    }

    @Override
    protected void setUp() throws Exception {
        dao = new PlacemarkDao(new RenamingDelegatingContext(getContext(), "test_"));
        dao.open();
    }

    @Override
    protected void tearDown() throws Exception {
        dao.close();
        dao = null;
        super.tearDown();
    }

    private void insertPompeiErcolanoVesuvio() {
        Placemark p = new Placemark();
        p.setName("Pompei");
        p.setDescription("Pompei city");
        p.setLatitude((float) POMPEI_LOCATION.getLatitude());
        p.setLongitude((float) POMPEI_LOCATION.getLongitude());
        p.setCollectionId(1);
        dao.insert(p);

        p = new Placemark();
        p.setName("Ercolano");
        p.setLatitude((float) ERCOLANO_LOCATION.getLatitude());
        p.setLongitude((float) ERCOLANO_LOCATION.getLongitude());
        p.setCollectionId(1);
        dao.insert(p);

        p = new Placemark();
        p.setName("Vesuvio");
        p.setLatitude((float) VESUVIO_LOCATION.getLatitude());
        p.setLongitude((float) VESUVIO_LOCATION.getLongitude());
        p.setCollectionId(2);
        dao.insert(p);
    }

    @Test
    public void testFindAllPlacemarkNear() throws Exception {
        insertPompeiErcolanoVesuvio();

        SortedSet<Placemark> set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 1, Arrays.asList(1L));
        assertEquals(1, set.size());
        final Placemark pompei = set.iterator().next();
        assertEquals("Pompei", pompei.getName());

        // empty catalog
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 1, Arrays.asList(2L));
        assertTrue(set.isEmpty());

        // no poi near vesuvio
        set = dao.findAllPlacemarkNear(VESUVIO_LOCATION, 1000, Arrays.asList(1L));
        assertTrue(set.isEmpty());

        // only Pompei
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 12000, Arrays.asList(1L, 999L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());

        // all data, Pompei first
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, Arrays.asList(1L));
        assertEquals(2, set.size());
        Iterator<Placemark> iterator = set.iterator();
        assertEquals("Pompei", iterator.next().getName());
        assertEquals("Ercolano", iterator.next().getName());

        // filter for Pompei
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, "pom", false, Arrays.asList(1L, 999L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());

        // filter favourite
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, "mpe", true, Arrays.asList(1L));
        assertTrue(set.isEmpty());

        PlacemarkAnnotation placemarkAnnotation = dao.loadPlacemarkAnnotation(pompei);
        placemarkAnnotation.setFlagged(true);
        dao.update(placemarkAnnotation);

        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, "mpe", true, Arrays.asList(1L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());
    }

    /**
     * Test aroung longitude 180
     */
    @Test
    public void testFindAllPlacemarkNearLongitude180() throws Exception {
        // insert some other point
        insertPompeiErcolanoVesuvio();

        Placemark p = new Placemark();
        p.setName("p1");
        p.setLongitude(179.9F);
        p.setCollectionId(1);
        dao.insert(p);

        p = new Placemark();
        p.setName("p2");
        p.setLongitude(-179.9F);
        p.setCollectionId(1);
        dao.insert(p);

        Location l = new Location(PlacemarkDaoTest.class.getSimpleName());
        l.setLatitude(0);
        l.setLongitude(179.9F);

        SortedSet<Placemark> set = dao.findAllPlacemarkNear(l, 100000, Arrays.asList(1L));
        assertEquals(2, set.size());

        l.setLongitude(-179.9F);
        set = dao.findAllPlacemarkNear(l, 100000, Arrays.asList(1L));
        assertEquals(2, set.size());
    }

    @Test
    public void testFindAllPlacemarkNearMatrix() {
        Placemark p = new Placemark();
        for (int lon = -171; lon <= 171; lon += 9) {
            for (int lat = -84; lat <= 84; lat += 12) {
                final String name = "Placemark " + lat + ',' + lon;
                p.setId(0);
                p.setName(name);
                p.setLatitude(lat);
                p.setLongitude(lon);
                p.setCollectionId(1);
                dao.insert(p);

                final Location referenceLocation = Util.newLocation(lat + 4 * Math.cos(lon), lon + 3 * Math.cos(lat));
                SortedSet<Placemark> placemarks = dao.findAllPlacemarkNear(referenceLocation,
                        referenceLocation.distanceTo(Util.newLocation(p)), Arrays.asList(1L));
                assertEquals("Error on " + name, 1, placemarks.size());
                p = placemarks.first();
                assertEquals(name, p.getName());
            }
        }
    }

    @Test
    public void testInsert() throws Exception {
        insertPompeiErcolanoVesuvio();

        final List<Placemark> list = dao.findAllPlacemarkByCollectionId(1);
        assertEquals(2, list.size());
        Placemark p = list.get(0);
        assertEquals(1, p.getId());
        assertEquals("Pompei", p.getName());
        assertEquals("Pompei city", p.getDescription());
        assertEquals(POMPEI_LOCATION.getLatitude(), p.getLatitude());
        assertEquals(POMPEI_LOCATION.getLongitude(), p.getLongitude());
        assertEquals(1, p.getCollectionId());

        p = dao.getPlacemark(2);
        assertEquals(2, p.getId());
        assertEquals("Ercolano", p.getName());
        assertNull(p.getDescription());
        assertEquals(ERCOLANO_LOCATION.getLatitude(), p.getLatitude());
        assertEquals(ERCOLANO_LOCATION.getLongitude(), p.getLongitude());
        assertEquals(1, p.getCollectionId());
    }

    @Test
    public void testDeleteByCollectionId() throws Exception {
        insertPompeiErcolanoVesuvio();

        dao.deleteByCollectionId(999);
        assertNotNull(dao.getPlacemark(1));
        assertNotNull(dao.getPlacemark(2));
        assertNotNull(dao.getPlacemark(3));

        dao.deleteByCollectionId(1);
        assertNull(dao.getPlacemark(1));
        assertNull(dao.getPlacemark(2));
        assertNotNull(dao.getPlacemark(3));
    }

    @Test
    public void testPlacemarkAnnotation() throws Exception {
        insertPompeiErcolanoVesuvio();
        final Placemark p = dao.getPlacemark(1);

        // load empty annotation
        PlacemarkAnnotation pa = dao.loadPlacemarkAnnotation(p);
        assertEquals(p.getLatitude(), pa.getLatitude());
        assertEquals(p.getLongitude(), pa.getLongitude());
        assertEquals("", pa.getNote());
        assertEquals(false, pa.isFlagged());

        // test insert
        pa.setNote("test note");
        dao.update(pa);
        assertTrue(pa.getId() > 0);

        pa = dao.loadPlacemarkAnnotation(p);
        assertEquals(p.getLatitude(), pa.getLatitude());
        assertEquals(p.getLongitude(), pa.getLongitude());
        assertEquals("test note", pa.getNote());
        assertEquals(false, pa.isFlagged());

        // test update
        pa.setNote("");
        pa.setFlagged(true);
        dao.update(pa);

        pa = dao.loadPlacemarkAnnotation(p);
        assertEquals(p.getLatitude(), pa.getLatitude());
        assertEquals(p.getLongitude(), pa.getLongitude());
        assertEquals("", pa.getNote());
        assertEquals(true, pa.isFlagged());

        // test delete
        pa.setNote("");
        pa.setFlagged(false);
        dao.update(pa);

        pa = dao.loadPlacemarkAnnotation(p);
        assertEquals(0, pa.getId());
    }
}