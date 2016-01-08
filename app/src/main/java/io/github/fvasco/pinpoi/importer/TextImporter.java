package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Import ASCII/UTF-8 file in format: <code>longitude, latitude, "POI name"</code>
 *
 * @author Francesco Vasco
 */
public class TextImporter extends AbstractImporter {
    @Override
    protected void importImpl(InputStream inputStream) throws IOException {
        final LineNumberReader reader = new LineNumberReader(new InputStreamReader(inputStream, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            final String[] parts = line.trim().split("\\s+", 3);
            if (parts.length == 3) try {
                final Placemark placemark = new Placemark();
                placemark.setLongitude(Float.parseFloat(parts[0]));
                placemark.setLatitude(Float.parseFloat(parts[1]));
                // remove double double-quotes
                String name = parts[2];
                if (name.length() > 2 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
                }
                placemark.setName(name);
                importPlacemark(placemark);
            } catch (final NumberFormatException nfe) {
                Log.d("importer", "Skip line " + line);
            }
        }
    }
}
