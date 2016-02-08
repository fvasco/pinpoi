package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import org.junit.Test;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class BackupManagerTest extends AndroidTestCase {

    private Context testContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        testContext = new RenamingDelegatingContext(getContext(), "test_");
        BackupManager.BACKUP_FILE.delete();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        BackupManager.BACKUP_FILE.delete();
    }

    @Test
    public void testCreate() throws Exception {
        if (!BackupManager.isCreateBackupSupported()) return;
        try (final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(testContext).open()) {
            final PlacemarkCollection placemarkCollection = new PlacemarkCollection();
            placemarkCollection.setName("name");
            placemarkCollection.setDescription("description");
            placemarkCollection.setCategory("category");
            placemarkCollection.setSource("source");
            placemarkCollectionDao.insert(placemarkCollection);
        }
        final BackupManager backupManager = new BackupManager(testContext);
        backupManager.create();
        assertTrue(BackupManager.BACKUP_FILE.isFile());
    }

    @Test
    public void testRestore() throws Exception {
        if (!BackupManager.isCreateBackupSupported()
                || !BackupManager.isRestoreBackupSupported()) return;

        testCreate();

        final PlacemarkCollectionDao placemarkCollectionDao = new PlacemarkCollectionDao(testContext);
        placemarkCollectionDao.open();
        try {
            for (final PlacemarkCollection placemarkCollection : placemarkCollectionDao.findAllPlacemarkCollection()) {
                placemarkCollectionDao.delete(placemarkCollection);
            }
        } finally {
            placemarkCollectionDao.close();
        }

        final BackupManager backupManager = new BackupManager(testContext);
        backupManager.restore();

        placemarkCollectionDao.open();
        try {
            assertTrue(!placemarkCollectionDao.findAllPlacemarkCollection().isEmpty());
        } finally {
            placemarkCollectionDao.close();
        }
    }
}