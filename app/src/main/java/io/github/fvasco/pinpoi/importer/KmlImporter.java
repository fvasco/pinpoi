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
    @Override
    protected void handleStartTag() {
        switch (tag) {
            case "Placemark":
                newPlacemark();
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
                    try (final InputStream inputStream = new URL(href).openStream()) {
                        delegateImporter.configureFrom(this);
                        delegateImporter.importPlacemarks(inputStream);
                    }
                }
            }
        } else {
            switch (tag) {
                case "Placemark":
                    importPlacemark();
                    break;
                case "name":
                    placemark.setName(text);
                    break;
                case "description":
                    placemark.setDescription(text);
                    break;
                case "coordinates":
                    // format: longitude, latitute, altitude
                    final String[] coordinates = text.split(",", 3);
                    placemark.setLongitude(Float.parseFloat(coordinates[0]));
                    placemark.setLatitude(Float.parseFloat(coordinates[1]));
                    break;
            }
        }
    }
}