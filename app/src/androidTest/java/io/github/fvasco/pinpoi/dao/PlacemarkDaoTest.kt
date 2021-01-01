package io.github.fvasco.pinpoi.dao

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.util.Coordinates
import io.github.fvasco.pinpoi.util.distanceTo
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
class PlacemarkDaoTest {

    lateinit var instrumentationContext: Context

    val POMPEI_LOCATION: Coordinates = Coordinates(40.7491819f, 14.5007385f)
    val ERCOLANO_LOCATION: Coordinates = Coordinates(40.8060768f, 14.3529209f)
    val VESUVIO_LOCATION: Coordinates = Coordinates(40.816667f, 14.433333f)

    private lateinit var dao: PlacemarkDao

    @Before
    fun setUp() {
        instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        dao = PlacemarkDao(instrumentationContext)
        dao.open()
    }

    @After
    fun tearDown() {
        dao.close()
    }

    private fun insertPompeiErcolanoVesuvio() {
        var p = Placemark()
        p.name = "Pompei"
        p.description = "Pompei city"
        p.coordinates = POMPEI_LOCATION
        p.collectionId = 1
        dao.insert(p)

        p = Placemark()
        p.name = "Ercolano"
        p.coordinates = ERCOLANO_LOCATION
        p.collectionId = 1
        dao.insert(p)

        p = Placemark()
        p.name = "Vesuvio"
        p.coordinates = VESUVIO_LOCATION
        p.collectionId = 2
        dao.insert(p)
    }

    @Test
    fun testFindAllPlacemarkNear() {
        insertPompeiErcolanoVesuvio()

        var set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 1.0, setOf(1L))
        assertEquals(1, set.size)
        val pompei = set.iterator().next()
        assertEquals("Pompei", pompei.name)

        // empty catalog
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 1.0, setOf(2L))
        assertTrue(set.isEmpty())

        // no poi near vesuvio
        set = dao.findAllPlacemarkNear(VESUVIO_LOCATION, 1000.0, setOf(1L))
        assertTrue(set.isEmpty())

        // only Pompei
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 12000.0, listOf(1L, 999L))
        assertEquals(1, set.size)
        assertEquals("Pompei", set.iterator().next().name)

        // all data, Pompei first
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000.0, setOf(1L))
        assertEquals(2, set.size)
        val iterator = set.iterator()
        assertEquals("Pompei", iterator.next().name)
        assertEquals("Ercolano", iterator.next().name)

        // filter for Pompei
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000.0, listOf(1L, 999L), "pom", false)
        assertEquals(1, set.size)
        assertEquals("Pompei", set.iterator().next().name)

        // filter favourite
        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000.0, setOf(1L), "mpe", true)
        assertTrue(set.isEmpty())

        val placemarkAnnotation = dao.loadPlacemarkAnnotation(pompei)
        placemarkAnnotation.flagged = true
        dao.update(placemarkAnnotation)

        set = dao.findAllPlacemarkNear(POMPEI_LOCATION, 14000.0, setOf(1L), "mpe", true)
        assertEquals(1, set.size)
        assertEquals("Pompei", set.iterator().next().name)
    }

    /**
     * Test aroung longitude 180
     */
    @Test
    fun testFindAllPlacemarkNearLongitude180() {
        // insert some other point
        insertPompeiErcolanoVesuvio()

        var p = Placemark()
        p.name = "p1"
        p.coordinates = Coordinates(0f, 179.9f)
        p.collectionId = 1
        dao.insert(p)

        p = Placemark()
        p.name = "p2"
        p.coordinates = Coordinates(0f, -179.9f)
        p.collectionId = 1
        dao.insert(p)

        var coordinates: Coordinates
        coordinates = Coordinates(0f, 179.9f)

        var set = dao.findAllPlacemarkNear(coordinates, 100000.0, setOf(1L))
        assertEquals(2, set.size)

        coordinates = Coordinates(0f, -179.9f)
        set = dao.findAllPlacemarkNear(coordinates, 100000.0, setOf(1L))
        assertEquals(2, set.size)
    }

    @Test
    fun testFindAllPlacemarkNearMatrix() {
        val p = Placemark()
        var lon = -171
        while (lon <= 171) {
            var lat = -84
            while (lat <= 84) {
                val name = "Placemark $lat,$lon"
                p.id = 0
                p.name = name
                p.coordinates = Coordinates(lat.toFloat(), lon.toFloat())
                p.collectionId = 1
                dao.insert(p)

                val referenceLocation =
                    Coordinates((lat + 4 * cos(lon.toDouble())).toFloat(), (lon + 3 * cos(lat.toDouble())).toFloat())
                val placemarks = dao.findAllPlacemarkNear(
                    referenceLocation,
                    referenceLocation.distanceTo(p.coordinates).toDouble(), setOf(1L)
                )
                assertEquals("Error on $name", 1, placemarks.size)
                val psr = placemarks.first()
                assertEquals(name, psr.name)
                lat += 12
            }
            lon += 9
        }
    }

    @Test
    fun testInsert() {
        insertPompeiErcolanoVesuvio()

        val list = dao.findAllPlacemarkByCollectionId(1)
        assertEquals(2, list.size)
        val p = list[0]
        assertEquals(1, p.id)
        assertEquals("Pompei", p.name)
        assertEquals("Pompei city", p.description)
        assertEquals(POMPEI_LOCATION, p.coordinates)
        assertEquals(1, p.collectionId)

        val dbp = dao.getPlacemark(2) ?: error("'2' not found")
        assertEquals(2, dbp.id)
        assertEquals("Ercolano", dbp.name)
        assertEquals("", dbp.description)
        assertEquals(ERCOLANO_LOCATION, dbp.coordinates)
        assertEquals(1, dbp.collectionId)
    }

    @Test
    fun testDeleteByCollectionId() {
        insertPompeiErcolanoVesuvio()

        dao.deleteByCollectionId(999)
        assertNotNull(dao.getPlacemark(1))
        assertNotNull(dao.getPlacemark(2))
        assertNotNull(dao.getPlacemark(3))

        dao.deleteByCollectionId(1)
        assertNull(dao.getPlacemark(1))
        assertNull(dao.getPlacemark(2))
        assertNotNull(dao.getPlacemark(3))
    }

    @Test
    fun testPlacemarkAnnotation() {
        insertPompeiErcolanoVesuvio()
        val p = dao.getPlacemark(1) ?: error("'1' not found")

        // load empty annotation
        var pa = dao.loadPlacemarkAnnotation(p)
        assertEquals(p.coordinates, pa.coordinates)
        assertEquals("", pa.note)
        assertEquals(false, pa.flagged)

        // test insert
        pa.note = "test note"
        dao.update(pa)

        pa = dao.loadPlacemarkAnnotation(p)
        assertEquals(p.coordinates, pa.coordinates)
        assertEquals("test note", pa.note)
        assertEquals(false, pa.flagged)

        // test update
        pa.note = ""
        pa.flagged = true
        dao.update(pa)

        pa = dao.loadPlacemarkAnnotation(p)
        assertEquals(p.coordinates, pa.coordinates)
        assertEquals("", pa.note)
        assertEquals(true, pa.flagged)

        // test delete
        pa.note = ""
        pa.flagged = false
        dao.update(pa)

        pa = dao.loadPlacemarkAnnotation(p)
        assertEquals("", pa.note)
        assertEquals(false, pa.flagged)
    }
}