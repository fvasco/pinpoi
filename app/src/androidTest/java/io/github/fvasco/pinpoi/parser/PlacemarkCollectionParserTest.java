package io.github.fvasco.pinpoi.parser;

import android.test.AndroidTestCase;

import org.junit.Test;

import java.util.List;

import io.github.fvasco.pinpoi.model.PlacemarkCollection;

/**
 * @author Francesco Vasco
 */
public class PlacemarkCollectionParserTest extends AndroidTestCase {

    @Test
    public void testRead() throws Exception {
        final PlacemarkCollectionParser pct = new PlacemarkCollectionParser();
        pct.setLocale("ITA");
        final List<PlacemarkCollection> list = pct.read(PlacemarkCollectionParserTest.class.getResource("placemarkCollection.zip"));

        assertEquals(2, list.size());

        PlacemarkCollection pc;
        pc = list.get(0);
        assertEquals("Test 1", pc.getName());
        assertEquals("Description 1", pc.getDescription());
        assertEquals("Category 1", pc.getCategory());
        assertEquals("Source 1", pc.getSource());

        pc = list.get(1);
        assertEquals("Test 2", pc.getName());
        assertEquals("Descrizione 2", pc.getDescription());
        assertEquals("Category 2", pc.getCategory());
        assertEquals("Source 2", pc.getSource());
    }
}