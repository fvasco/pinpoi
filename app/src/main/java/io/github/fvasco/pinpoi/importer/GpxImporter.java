package io.github.fvasco.pinpoi.importer;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * KML importer
 *
 * @author Francesco Vasco
 */
/*
 * Read KML data using SAX
 */
public class GpxImporter extends AbstractImporter {

    private final SAXParser saxParser;

    public GpxImporter() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            saxParser = spf.newSAXParser();
        } catch (SAXException | ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void importImpl(InputStream is) throws IOException {
        try {
            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setContentHandler(new GpxContentHandler());
            xmlReader.parse(new InputSource(is));
        } catch (SAXException ex) {
            throw new IOException("Error reading GPX file", ex);
        }
    }

    private final class GpxContentHandler extends DefaultHandler implements ContentHandler {

        private final StringBuilder stringBuilder = new StringBuilder();
        private Placemark placemark;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            stringBuilder.append(ch, start, length);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("wpt".equals(localName)) {
                placemark = new Placemark();
                placemark.setLongitude(Float.parseFloat(attributes.getValue("lon")));
                placemark.setLatitude(Float.parseFloat(attributes.getValue("lat")));
            }
            stringBuilder.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (placemark != null && stringBuilder.length() > 0) {
                final String string = stringBuilder.toString().trim();
                switch (localName) {
                    case "wpt":
                        importPlacemark(placemark);
                        placemark = null;
                        break;
                    case "name":
                        placemark.setName(string);
                        break;
                    case "desc":
                        placemark.setDescription(string);
                        break;
                }
            }
            stringBuilder.setLength(0);
        }
    }

}
