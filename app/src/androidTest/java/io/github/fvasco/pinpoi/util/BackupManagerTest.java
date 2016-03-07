package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.io.File;
import java.util.List;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class BackupManagerTest extends AndroidTestCase {

    private File backupFile;
    private PlacemarkCollectionDao placemarkCollectionDao;
    private PlacemarkDao placemarkDao;
    private BackupManager backupManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context testContext = new RenamingDelegatingContext(getContext(), "test_");
        backupFile = new File(testContext.getCacheDir(), "test.backup");
        placemarkCollectionDao = new PlacemarkCollectionDao(testContext);
        placemarkDao = new PlacemarkDao(testContext);
        //noinspection ResultOfMethodCallIgnored
        if (backupFile.exists()) {
            assertTrue(backupFile.delete());
        }

        // init database
        DebugUtil.setUpDebugDatabase(testContext);
        backupManager = new BackupManager(placemarkCollectionDao, placemarkDao);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        //noinspection ResultOfMethodCallIgnored
        backupFile.delete();
    }

    @Test
    public void testBackup() throws Exception {
        // create
        backupManager.create(backupFile);
        assertTrue(backupFile.length() > 0);

        // restore
        placemarkCollectionDao.open();
        placemarkDao.open();
        placemarkCollectionDao.getDatabase().beginTransaction();
        placemarkDao.getDatabase().beginTransaction();

        final int placemarkCollectionCount;
        final long placemarkCollectionId;
        final int placemarkCount;
        List<PlacemarkCollection> allPlacemarkCollection = placemarkCollectionDao.findAllPlacemarkCollection();
        placemarkCollectionCount = allPlacemarkCollection.size();
        placemarkCollectionId = allPlacemarkCollection.get(0).getId();
        placemarkCount = placemarkDao.findAllPlacemarkByCollectionId(placemarkCollectionId).size();

        for (final PlacemarkCollection placemarkCollection : placemarkCollectionDao.findAllPlacemarkCollection()) {
            placemarkDao.findAllPlacemarkByCollectionId(placemarkCollection.getId());
            placemarkCollectionDao.delete(placemarkCollection);
        }

        placemarkCollectionDao.getDatabase().setTransactionSuccessful();
        placemarkDao.getDatabase().setTransactionSuccessful();
        placemarkCollectionDao.getDatabase().endTransaction();
        placemarkDao.getDatabase().endTransaction();
        placemarkCollectionDao.close();
        placemarkDao.close();

        backupManager.restore(backupFile);

        placemarkCollectionDao.open();
        placemarkDao.open();
        assertEquals(placemarkCollectionCount, placemarkCollectionDao.findAllPlacemarkCollection().size());
        assertEquals(placemarkCount, placemarkDao.findAllPlacemarkByCollectionId(placemarkCollectionId).size());
        placemarkCollectionDao.close();
        placemarkDao.close();
    }
}