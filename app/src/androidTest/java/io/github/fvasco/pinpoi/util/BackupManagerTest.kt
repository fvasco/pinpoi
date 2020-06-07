package io.github.fvasco.pinpoi.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * @author Francesco Vasco
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    lateinit var instrumentationContext: Context

    private lateinit var backupFile: File
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    private lateinit var placemarkDao: PlacemarkDao
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        backupFile = File(instrumentationContext.cacheDir, "test.backup")
        placemarkCollectionDao = PlacemarkCollectionDao(instrumentationContext)
        placemarkDao = PlacemarkDao(instrumentationContext)
        //noinspection ResultOfMethodCallIgnored
        if (backupFile.exists()) {
            assertTrue(backupFile.delete())
        }

        // init database
        setUpDebugDatabase(instrumentationContext)
        backupManager = BackupManager(placemarkCollectionDao, placemarkDao)
    }

    @After
    fun tearDown() {
        //noinspection ResultOfMethodCallIgnored
        backupFile.delete()
    }

    @Test
    fun testBackup() {
        // create
        backupManager.create(backupFile)
        assertTrue(backupFile.length() > 0)

        // restore
        placemarkCollectionDao.open()
        placemarkDao.open()
        placemarkCollectionDao.database!!.beginTransaction()
        placemarkDao.database!!.beginTransaction()

        val placemarkCollectionCount: Int
        val placemarkCollectionId: Long
        val placemarkCount: Int
        val allPlacemarkCollection = placemarkCollectionDao.findAllPlacemarkCollection()
        placemarkCollectionCount = allPlacemarkCollection.size
        placemarkCollectionId = allPlacemarkCollection[0].id
        placemarkCount = placemarkDao.findAllPlacemarkByCollectionId(placemarkCollectionId).size

        for (placemarkCollection in placemarkCollectionDao.findAllPlacemarkCollection()) {
            placemarkDao.findAllPlacemarkByCollectionId(placemarkCollection.id)
            placemarkCollectionDao.delete(placemarkCollection)
        }

        placemarkCollectionDao.database!!.setTransactionSuccessful()
        placemarkDao.database!!.setTransactionSuccessful()
        placemarkCollectionDao.database!!.endTransaction()
        placemarkDao.database!!.endTransaction()
        placemarkCollectionDao.close()
        placemarkDao.close()

        backupManager.restore(backupFile)

        placemarkCollectionDao.open()
        placemarkDao.open()
        assertEquals(placemarkCollectionCount, placemarkCollectionDao.findAllPlacemarkCollection().size)
        assertEquals(placemarkCount, placemarkDao.findAllPlacemarkByCollectionId(placemarkCollectionId).size)
        placemarkCollectionDao.close()
        placemarkDao.close()
    }
}