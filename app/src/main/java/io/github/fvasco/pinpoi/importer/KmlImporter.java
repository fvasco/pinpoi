package io.github.fvasco.pinpoi.importer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * KML importer
 *
 * @author Francesco Vasco
 */
public class KmlImporter extends AbstractXmlImporter {

    private double latitude, longitude;
    private int coordinateCount;

    @Override
    protected void handleStartTag() {
        switch (tag) {
            case "Placemark":
                newPlacemark();
                latitude = 0;
                longitude = 0;
                coordinateCount = 0;
        }
    }

    @Override
    protected void handleEndTag() throws IOException {
        if (placemark == null) {
            if ("href".equals(tag) && checkCurrentPath("kml", "Document", "NetworkLink", "Url")) {
                final String href = text;
                final AbstractImporter delegateImporter = ImporterFacade.createImporter(href);
                Log.i(KmlImporter.class.getSimpleName(), "NetworkLink href " + href + " importer " + delegateImporter);
                if (delegateImporter != null) {
                    final InputStream inputStream = new URL(href).openStream();
                    try {
                        delegateImporter.configureFrom(this);
                        delegateImporter.importPlacemarks(inputStream);
                    } finally {
                        inputStream.close();
                    }
                }
            }
        } else {
            switch (tag) {
                case "Placemark":
                    if (coordinateCount > 0) {
                        // set placemark to center
                        placemark.setLongitude((float) (latitude / (double) coordinateCount));
                        placemark.setLatitude((float) (longitude / (double) coordinateCount));
                    }
                    importPlacemark();
                    break;
                case "name":
                    placemark.setName(text);
                    break;
                case "description":
                    placemark.setDescription(text);
                    break;
                case "coordinates":
                    // read multiple lines if present (point, line, polygon)
                    for (final String line : text.trim().split("\\s+")) {
                        // format: longitude, latitute, altitude
                        final String[] coordinates = line.split(",", 3);
                        latitude += Double.parseDouble(coordinates[0]);
                        longitude += Double.parseDouble(coordinates[1]);
                        ++coordinateCount;
                    }
                    break;
            }
        }
    }
}