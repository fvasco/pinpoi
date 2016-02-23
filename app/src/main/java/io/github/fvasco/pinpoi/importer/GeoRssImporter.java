package io.github.fvasco.pinpoi.importer;

/**
 * Import simple geo RSS/Atom
 *
 * @author Francesco Vasco
 */
public class GeoRssImporter extends AbstractXmlImporter {
    @Override
    protected void handleStartTag() {
        switch (tag) {
            case "item": // RSS
            case "entry": // ATOM
                newPlacemark();
                break;
        }
    }

    @Override
    protected void handleEndTag() {
        if (placemark == null) return;
        switch (tag) {
            case "item": // RSS
            case "entry": // ATOM
                importPlacemark();
                break;
            case "title":
                placemark.setName(text);
                break;
            case "description": // Atom
            case "summary": // RSS
                placemark.setDescription(text);
                break;
            case "point": // Atom
                String[] parts = text.split("[ ,]+", 3);
                if (parts.length >= 2) {
                    placemark.setLatitude(Float.parseFloat(parts[0]));
                    placemark.setLongitude(Float.parseFloat(parts[1]));
                }
                break;
            case "lat": // RSS
                placemark.setLatitude(Float.parseFloat(text));
                break;
            case "long": // RSS
                placemark.setLongitude(Float.parseFloat(text));
                break;
        }
    }
}
