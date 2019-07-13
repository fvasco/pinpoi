package io.github.fvasco.pinpoi.util

import android.test.AndroidTestCase
import android.test.RenamingDelegatingContext
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import org.junit.Test
import java.io.File

/**
 * @author Francesco Vasco
 */
class BackupManagerTest : AndroidTestCase() {

    private lateinit var backupFile: File
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    private lateinit var placemarkDao: PlacemarkDao
    private lateinit var backupManager: BackupManager

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        val testContext = RenamingDelegatingContext(context, "test_")
        backupFile = File(testContext.cacheDir, "test.backup")
        placemarkCollectionDao = PlacemarkCollectionDao(testContext)
        placemarkDao = PlacemarkDao(testContext)
        //noinspection ResultOfMethodCallIgnored
        if (backupFile.exists()) {
            assertTrue(backupFile.delete())
        }

        // init database
        setUpDebugDatabase(testContext)
        backupManager = BackupManager(placemarkCollectionDao, placemarkDao)
    }

    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
        //noinspection ResultOfMethodCallIgnored
        backupFile.delete()
    }

    @Test
    @Throws(Exception::class)
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