package io.github.fvasco.pinpoi.util;

import android.location.Location;
import android.test.AndroidTestCase;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class UtilTest extends AndroidTestCase {

    @Test
    public void testNewLocation() throws Exception {
        final Location l = Util.newLocation(1, 2);
        assertEquals(1.0, l.getLatitude());
        assertEquals(2.0, l.getLongitude());
    }

    @Test
    public void testFormatCoordinate() throws Exception {
        final Placemark p = new Placemark();
        p.setLatitude(1.2F);
        p.setLongitude(3.4F);
        assertEquals("1.2,3.4", Util.formatCoordinate(p));
    }

    @Test
    public void testIsEmpty() throws Exception {
        // string
        assertTrue(Util.isEmpty((String) null));
        assertTrue(Util.isEmpty(""));
        assertTrue(!Util.isEmpty("x"));

        // collection
        assertTrue(Util.isEmpty((Collection) null));
        assertTrue(Util.isEmpty(Collections.EMPTY_LIST));
        assertTrue(!Util.isEmpty(Arrays.asList(1)));
    }

    @Test
    public void testIsUri() throws Exception {
        assertTrue(!Util.isUri(null));
        assertTrue(!Util.isUri(""));
        assertTrue(!Util.isUri("/path/file.ext"));
        assertTrue(!Util.isUri("name.ext"));
        assertTrue(Util.isUri("file:///path/file"));
        assertTrue(Util.isUri("http://server.domain/resource.txt"));
    }
}