package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.fvasco.pinpoi.util.ZipGuardInputStream;

/**
 * Import KMZ file (all KML in a ZIP)
 *
 * @author Francesco Vasco
 */
public class KmzImporter extends KmlImporter {

    @Override
    protected void importImpl(InputStream inputStream) throws IOException {
        try (final ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".kml")) {
                    Log.d(KmzImporter.class.getSimpleName(), "Import entry " + zipEntry.getName());
                    super.importImpl(new ZipGuardInputStream(zipInputStream));
                }
            }
        }
    }
}
