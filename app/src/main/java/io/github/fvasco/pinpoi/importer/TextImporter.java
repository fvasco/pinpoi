package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * Import ASCII/UTF-8 file in format
 * <ul>
 * <li>ASC: <code>longitude latitude "POI name"</code></li>
 * <li>CSV: <code>longitude, latitude, "POI name"</code></li>
 * <li>CSV: <code>longitude; latitude; "POI name"</code></li>
 * </ul>
 *
 * @author Francesco Vasco
 */
public class TextImporter extends AbstractImporter {

    private static final Pattern LINE_PATTERN = Pattern.compile("\\s*(\"?)([+-]?\\d+\\.\\d+)\\1[,;\\s]+(\"?)([+-]?\\d+\\.\\d+)\\3[,;\\s]+\"(.*)\"\\s*");

    @Override
    protected void importImpl(InputStream inputStream) throws IOException {
        final LineNumberReader reader = new LineNumberReader(new InputStreamReader(inputStream, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            final Matcher matcher = LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    final Placemark placemark = new Placemark();
                    placemark.setLongitude(Float.parseFloat(matcher.group(2)));
                    placemark.setLatitude(Float.parseFloat(matcher.group(4)));
                    // remove double double-quotes
                    placemark.setName(matcher.group(5).replace("\"\"", "\""));
                    importPlacemark(placemark);
                } catch (final NumberFormatException nfe) {
                    Log.d("importer", "Skip line: " + line, nfe);
                }
            } else {
                Log.d("importer", "Skip line: " + line);
            }
        }
    }
}
