package io.github.fvasco.pinpoi.importer;

/**
 * KML importer
 *
 * @author Francesco Vasco
 */
/*
 * Read KML data using SAX
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
    protected void handleEndTag() {
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
