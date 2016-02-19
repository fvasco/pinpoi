package io.github.fvasco.pinpoi.util;

import android.test.AndroidTestCase;

import org.junit.Test;

import io.github.fvasco.pinpoi.model.Placemark;

/**
 * @author Francesco Vasco
 */
public class LocationUtilTest extends AndroidTestCase {

    @Test
    public void testFormatCoordinate() throws Exception {
        final Placemark p = new Placemark();
        p.setLatitude(1.2F);
        p.setLongitude(3.4F);
        assertEquals("1.2,3.4", LocationUtil.formatCoordinate(p));
    }

}