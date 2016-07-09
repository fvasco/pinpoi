package io.github.fvasco.pinpoi.importer

import android.test.RenamingDelegatingContext
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import org.junit.Test
import java.io.IOException

/**
 * @author Francesco Vasco
 */
class ImporterFacadeTest : AbstractImporterTestCase() {

    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    private lateinit var placemarkDao: PlacemarkDao

    override fun setUp() {
        super.setUp()
        context = RenamingDelegatingContext(getContext(), "test_")
        placemarkCollectionDao = PlacemarkCollectionDao(context)
        placemarkDao = PlacemarkDao(context)
        placemarkCollectionDao.open()
        placemarkDao.open()
    }

    override fun tearDown() {
        super.tearDown()
        placemarkDao.close()
        placemarkCollectionDao.close()
    }

    private fun insertPlacemarkCollection(resource: String): PlacemarkCollection {
        val pc = PlacemarkCollection()
        pc.name = resource
        val resourceUrl = javaClass.getResource(resource)
        pc.source = if (resourceUrl == null) resource else resourceUrl.toString()
        placemarkCollectionDao.insert(pc)
        return pc
    }

    @Test
    fun testImportPlacemarksKml() {
        val pc = insertPlacemarkCollection("test2.kml")
        val importerFacade = ImporterFacade(context)
        val count = importerFacade.importPlacemarks(pc)
        assertEquals(2, count)

        assertEquals(count, pc.poiCount)
        val lastUpdate = pc.lastUpdate
        assertTrue(lastUpdate > 0)

        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(pc.id).size)
    }

    @Test
    fun testImportPlacemarksWrong() {
        val pc = insertPlacemarkCollection("wrong")
        pc.lastUpdate = 1
        pc.poiCount = 10
        val importerFacade = ImporterFacade(context)
        try {
            importerFacade.importPlacemarks(pc)
            fail()
        } catch (e: IOException) {
            // ok
        }

        assertEquals(1, pc.lastUpdate)
        assertEquals(10, pc.poiCount)
    }

    @Test
    fun testImportPlacemarksAsc() {
        val pc = insertPlacemarkCollection("asc.txt")
        val importerFacade = ImporterFacade(context)
        val count = importerFacade.importPlacemarks(pc)
        assertEquals(3, count)
        assertEquals(count, pc.poiCount)
        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(pc.id).size)
    }

    @Test
    fun testImportPlacemarksCsvLanLon() {
        val pc = insertPlacemarkCollection("csv.txt")
        val importerFacade = ImporterFacade(context)
        val count = importerFacade.importPlacemarks(pc)
        importerFacade.fileFormatFilter = FileFormatFilter.CSV_LAT_LON
        assertEquals(3, count)
        assertEquals(count, pc.poiCount)
        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(pc.id).size)
    }

    @Test
    fun testImportPlacemarksCsvLonLat() {
        val pc = insertPlacemarkCollection("csv.txt")
        val importerFacade = ImporterFacade(context)
        importerFacade.fileFormatFilter = FileFormatFilter.CSV_LON_LAT
        val count = importerFacade.importPlacemarks(pc)
        assertEquals(4, count)
        assertEquals(count, pc.poiCount)
        assertEquals(count, placemarkDao.findAllPlacemarkByCollectionId(pc.id).size)
    }
}