package io.github.fvasco.pinpoi.importer;

import io.github.fvasco.pinpoi.util.Util;

/**
 * GPX importer
 *
 * @author Francesco Vasco
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
        if (placemark == null) return;
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
