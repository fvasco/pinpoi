package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.fvasco.pinpoi.util.ZipGuardInputStream;

/**
 * Import ZIP and KMZ file
 *
 * @author Francesco Vasco
 */
public class ZipImporter extends AbstractImporter {


    @Override
    protected void importImpl(InputStream inputStream) throws IOException {
        try (final ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    final String entryName = zipEntry.getName();
                    final AbstractImporter importer = ImporterFacade.createImporter(entryName);
                    if (importer != null) {
                        Log.d(ZipImporter.class.getSimpleName(), "Import entry " + entryName);
                        importer.setCollectionId(collectionId);
                        importer.setConsumer(consumer);
                        importer.importImpl(new ZipGuardInputStream(zipInputStream));
                    }
                }
            }
        }
    }
}