package io.github.fvasco.pinpoi.dao;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.model.PlacemarkSearchResult;
import io.github.fvasco.pinpoi.util.Coordinates;

/**
 * @author Francesco Vasco
 */
public class PlacemarkDaoTest extends AndroidTestCase {
    public static final Coordinates POMPEI_LOCATION;
    public static final Coordinates ERCOLANO_LOCATION;
    public static final Coordinates VESUVIO_LOCATION;

    static {
        // Pompei
        POMPEI_LOCATION = new Coordinates(40.7491819F, 14.5007385F);
        // Ercolano
        ERCOLANO_LOCATION = new Coordinates(40.8060768F, 14.3529209F);
        // Vesuvio
        VESUVIO_LOCATION = new Coordinates(40.816667F, 14.433333F);
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
        p.setLatitude(POMPEI_LOCATION.getLatitude());
        p.setLongitude(POMPEI_LOCATION.getLongitude());
        p.setCollectionId(1);
        dao.insert(p);

        p = new Placemark();
        p.setName("Ercolano");
        p.setLatitude(ERCOLANO_LOCATION.getLatitude());
        p.setLongitude(ERCOLANO_LOCATION.getLongitude());
        p.setCollectionId(1);
        dao.insert(p);

        p = new Placemark();
        p.setName("Vesuvio");
        p.setLatitude(VESUVIO_LOCATION.getLatitude());
        p.setLongitude(VESUVIO_LOCATION.getLongitude());
        p.setCollectionId(2);
        dao.insert(p);
    }

    @Test
    public void testFindAllPlacemarkNear() throws Exception {
        insertPompeiErcolanoVesuvio();

        SortedSet<PlacemarkSearchResult> set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 1, Collections.singleton(1L));
        assertEquals(1, set.size());
        final PlacemarkSearchResult pompei = set.iterator().next();
        assertEquals("Pompei", pompei.getName());

        // empty catalog
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 1, Collections.singleton(2L));
        assertTrue(set.isEmpty());

        // no poi near vesuvio
        set = dao.findAllPlacemarkNear(VESUVIO_LOCATION, 1000, Collections.singleton(1L));
        assertTrue(set.isEmpty());

        // only Pompei
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 12000, Arrays.asList(1L, 999L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());

        // all data, Pompei first
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, Collections.singleton(1L));
        assertEquals(2, set.size());
        Iterator<PlacemarkSearchResult> iterator = set.iterator();
        assertEquals("Pompei", iterator.next().getName());
        assertEquals("Ercolano", iterator.next().getName());

        // filter for Pompei
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, "pom", false, Arrays.asList(1L, 999L));
        assertEquals(1, set.size());
        assertEquals("Pompei", set.iterator().next().getName());

        // filter favourite
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, "mpe", true, Collections.singleton(1L));
        assertTrue(set.isEmpty());

        PlacemarkAnnotation placemarkAnnotation = dao.loadPlacemarkAnnotation(pompei);
        placemarkAnnotation.setFlagged(true);
        dao.update(placemarkAnnotation);

        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000, "mpe", true, Collections.singleton(1L));
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

        Coordinates coordinates;
        coordinates = new Coordinates(0, 179.9F);

        SortedSet<PlacemarkSearchResult> set = dao.findAllPlacemarkNear(coordinates, 100000, Collections.singleton(1L));
        assertEquals(2, set.size());

        coordinates = new Coordinates(0, -179.9F);
        set = dao.findAllPlacemarkNear(coordinates, 100000, Collections.singleton(1L));
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

                final Coordinates referenceLocation = new Coordinates((float) (lat + 4 * Math.cos(lon)), (float) (lon + 3 * Math.cos(lat)));
                final SortedSet<PlacemarkSearchResult> placemarks = dao.findAllPlacemarkNear(referenceLocation,
                        referenceLocation.distanceTo(Coordinates.fromPlacemark(p)), Collections.singleton(1L));
                assertEquals("Error on " + name, 1, placemarks.size());
                final PlacemarkSearchResult psr = placemarks.first();
                assertEquals(name, psr.getName());
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