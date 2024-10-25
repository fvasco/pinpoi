package io.github.fvasco.pinpoi.dao

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
class PlacemarkCollectionDaoTest {

    private lateinit var dao: PlacemarkCollectionDao

    lateinit var instrumentationContext: Context

    @Before
    fun setUp() {
        instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        dao = PlacemarkCollectionDao(instrumentationContext)
        dao.open()
        for (pc in dao.findAllPlacemarkCollection()) {
            dao.delete(pc)
        }
    }

    @After
    fun tearDown() {
        dao.close()
    }

    @Test
    fun testFindPlacemarkCollectionCategory() {
        val pc = PlacemarkCollection()
        pc.name = "test"
        pc.description = "description"
        pc.source = "source"
        pc.category = "CATEGORY"
        pc.lastUpdate = System.currentTimeMillis()
        dao.insert(pc)

        val list = dao.findAllPlacemarkCollectionCategory()
        assertEquals(1, list.size)
        val category = list[0]
        assertEquals("CATEGORY", category)
    }

    @Test
    fun testFindPlacemarkCollection() {
        val pc = PlacemarkCollection()
        pc.name = "test"
        pc.description = "description"
        pc.source = "source"
        pc.category = "CATEGORY"
        pc.lastUpdate = 1
        pc.poiCount = 2
        dao.insert(pc)

        val id = pc.id
        // check findPlacemarkCollectionById
        val dbpc = dao.findPlacemarkCollectionById(id) ?: error("not found")
        assertEquals(id, dbpc.id)
        assertEquals("test", dbpc.name)
        assertEquals("description", dbpc.description)
        assertEquals("source", dbpc.source)
        assertEquals("CATEGORY", dbpc.category)
        assertEquals(1, dbpc.lastUpdate)
        assertEquals(2, dbpc.poiCount)

        pc.name = "test2"
        pc.description = "description2"
        pc.source = "source2"
        pc.category = "CATEGORY2"
        pc.lastUpdate = 5
        pc.poiCount = 6
        dao.update(pc)

        // check findAllPlacemarkCollection
        val list1 = dao.findAllPlacemarkCollection()
        assertEquals(1, list1.size)
        val pc0 = list1[0]
        assertNotNull(pc0)
        assertEquals(id, pc0.id)
        assertEquals("test2", pc0.name)
        assertEquals("description2", pc0.description)
        assertEquals("source2", pc0.source)
        assertEquals("CATEGORY2", pc0.category)
        assertEquals(5, pc0.lastUpdate)
        assertEquals(6, pc0.poiCount)

        dao.delete(pc)
        val list0 = dao.findAllPlacemarkCollection()
        assertTrue(list0.isEmpty())
        assertNull(dao.findPlacemarkCollectionById(1))
    }

    @Test
    fun testFindPlacemarkCollectionByName() {
        val pc = PlacemarkCollection()
        pc.name = "test"
        pc.description = "description"
        pc.source = "source"
        pc.category = "CATEGORY"
        pc.lastUpdate = 5
        pc.poiCount = 6
        dao.insert(pc)

        val id = pc.id
        // check findPlacemarkCollectionByName
        val dbpc = dao.findPlacemarkCollectionByName("test") ?: error("test not found")
        assertEquals(id, dbpc.id)
        assertEquals("test", dbpc.name)
        assertEquals("description", dbpc.description)
        assertEquals("source", dbpc.source)
        assertEquals("CATEGORY", dbpc.category)
        assertEquals(5, dbpc.lastUpdate)
        assertEquals(6, dbpc.poiCount)
    }
}