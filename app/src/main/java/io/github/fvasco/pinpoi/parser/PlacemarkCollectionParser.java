package io.github.fvasco.pinpoi.parser;

import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * Import {@linkplain io.github.fvasco.pinpoi.model.PlacemarkCollection} listo from URL
 *
 * @author Francesco Vasco
 */
public class PlacemarkCollectionParser {

    private final SAXParser saxParser;
    private String locale = Locale.getDefault().getISO3Language();
    private List<PlacemarkCollection> placemarkCollections;

    public PlacemarkCollectionParser() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            saxParser = spf.newSAXParser();
        } catch (SAXException | ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public List<PlacemarkCollection> read(final URL url) throws IOException {
        Log.i(PlacemarkCollectionParser.class.getSimpleName(), "Read " + url + " using locale " + locale);
        try (final InputStream is = url.openStream()) {
            placemarkCollections = new ArrayList<>();
            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setContentHandler(new CollectionContentHandler());
            xmlReader.parse(new InputSource(is));
            return placemarkCollections;
        } catch (SAXException ex) {
            throw new IOException("Error reading XML file", ex);
        } finally {
            placemarkCollections = null;
        }
    }

    private final class CollectionContentHandler extends DefaultHandler implements ContentHandler {

        private final StringBuilder stringBuilder = new StringBuilder();
        private PlacemarkCollection placemarkCollection;
        private String lang;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            stringBuilder.append(ch, start, length);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("PlacemarkCollection".equals(localName)) {
                placemarkCollection = new PlacemarkCollection();
            }
            lang = attributes.getValue("lang");
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (placemarkCollection != null && stringBuilder.length() > 0) {
                final String string = stringBuilder.toString().trim();
                switch (localName) {
                    case "PlacemarkCollection":
                        placemarkCollections.add(placemarkCollection);
                        placemarkCollection = null;
                        break;
                    case "name":
                        if (placemarkCollection.getName() == null || locale.equals(lang))
                            placemarkCollection.setName(string);
                        break;
                    case "description":
                        if (placemarkCollection.getDescription() == null || locale.equals(lang))
                            placemarkCollection.setDescription(string);
                        break;
                    case "category":
                        if (placemarkCollection.getCategory() == null || locale.equals(lang))
                            placemarkCollection.setCategory(string);
                        break;
                    case "source":
                        if (placemarkCollection.getSource() == null || locale.equals(lang))
                            placemarkCollection.setSource(string);
                        break;
                }
                stringBuilder.setLength(0);
            }
        }
    }
}
