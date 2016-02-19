package io.github.fvasco.pinpoi.util;

import android.test.AndroidTestCase;

import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Francesco Vasco
 */
public class UtilTest extends AndroidTestCase {

    @Test
    public void testIsEmpty() throws Exception {
        // string
        assertTrue(Util.isEmpty((String) null));
        assertTrue(Util.isEmpty(""));
        assertTrue(!Util.isEmpty("x"));

        // collection
        assertTrue(Util.isEmpty((Collection) null));
        assertTrue(Util.isEmpty(Collections.EMPTY_LIST));
        assertTrue(!Util.isEmpty(Collections.singleton(1)));
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