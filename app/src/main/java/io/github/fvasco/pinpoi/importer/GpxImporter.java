package io.github.fvasco.pinpoi.importer;

import io.github.fvasco.pinpoi.util.Util;

/**
 * KML importer
 *
 * @author Francesco Vasco
 */
/*
 * Read KML data using SAX
 */
public class GpxImporter extends AbstractXmlImporter {

    @Override
    protected void handleStartTag() {
        switch (tag) {
            case "wpt":
                newPlacemark();
                placemark.setLatitude(Float.parseFloat(parser.getAttributeValue(null, "lat")));
                placemark.setLongitude(Float.parseFloat(parser.getAttributeValue(null, "lon")));
                break;
            case "link":
                if (placemark != null && Util.isEmpty(placemark.getDescription()))
                    placemark.setDescription(parser.getAttributeValue(null, "href"));
                break;
        }
    }

    @Override
    protected void handleEndTag() {
        switch (tag) {
            case "wpt":
                importPlacemark();
                break;
            case "name":
                placemark.setName(text);
                break;
            case "desc":
                placemark.setDescription(text);
                break;
        }
    }

}
