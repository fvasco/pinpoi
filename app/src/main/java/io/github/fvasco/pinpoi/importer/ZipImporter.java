package io.github.fvasco.pinpoi.importer;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.fvasco.pinpoi.util.ZipGuardInputStream;

/**
 * Import ZIP collection and KMZ file
 *
 * @author Francesco Vasco
 */
public class ZipImporter extends AbstractImporter {

    @Override
    protected void importImpl(@NonNull InputStream inputStream) throws IOException {
        try (final ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                final String entryName = zipEntry.getName();
                if (!zipEntry.isDirectory() && !entryName.startsWith(".")) {
                    final AbstractImporter importer = ImporterFacade.createImporter(entryName);
                    if (importer != null) {
                        Log.d(ZipImporter.class.getSimpleName(), "Import entry " + entryName);
                        importer.configureFrom(this);
                        importer.importImpl(new ZipGuardInputStream(zipInputStream));
                    }
                }
            }
        }
    }
}