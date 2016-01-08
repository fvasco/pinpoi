package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import KMZ file (all KML in a ZIP)
 *
 * @author Francesco Vasco
 */
public class KmzImporter extends KmlImporter {

    private final KmlImporter kmlImporter = new KmlImporter();

    @Override
    protected void importImpl(InputStream inputStream) throws IOException {
        try (final ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".kml")) {
                    Log.d("importer", "Import entry " + zipEntry.getName());
                    super.importImpl(new ZipGuardInputStream(zipInputStream));
                }
            }
        }
    }

    /**
     * Filter input stream to close all, this stream close only current entry;
     */
    private static final class ZipGuardInputStream extends FilterInputStream {

        protected ZipGuardInputStream(ZipInputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            ((ZipInputStream) this.in).closeEntry();
        }
    }
}
