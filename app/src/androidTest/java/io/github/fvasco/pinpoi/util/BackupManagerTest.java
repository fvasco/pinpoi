package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import java.io.File;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class BackupManagerTest extends AndroidTestCase {

    private Context testContext;
    private File backupFile;
    private PlacemarkCollectionDao placemarkCollectionDao;
    private PlacemarkDao placemarkDao;
    private BackupManager backupManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        testContext = new RenamingDelegatingContext(getContext(), "test_");
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
    public void testCreate() throws Exception {
        backupManager.create(backupFile);
        assertTrue(backupFile.length() > 0);
    }

    @Test
    public void testRestore() throws Exception {
        testCreate();

        try (final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(testContext).open();
             final PlacemarkDao placemarkDao = new PlacemarkDao(testContext).open()) {
            placemarkCollectionDao.getDatabase().beginTransaction();
            placemarkDao.getDatabase().beginTransaction();
            try {
                for (final PlacemarkCollection placemarkCollection : placemarkCollectionDao.findAllPlacemarkCollection()) {
                    placemarkDao.findAllPlacemarkByCollectionId(placemarkCollection.getId());
                    placemarkCollectionDao.delete(placemarkCollection);
                }
                placemarkCollectionDao.getDatabase().setTransactionSuccessful();
                placemarkDao.getDatabase().setTransactionSuccessful();
            } finally {
                placemarkCollectionDao.getDatabase().endTransaction();
                placemarkDao.getDatabase().endTransaction();
            }
        }
        backupManager.restore(backupFile);

        try (final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(testContext).open();
             final PlacemarkDao placemarkDao = new PlacemarkDao(testContext).open()) {
            assertTrue(!placemarkCollectionDao.findAllPlacemarkCollection().isEmpty());
            assertTrue(!placemarkDao.findAllPlacemarkByCollectionId(1).isEmpty());
        }
    }
}