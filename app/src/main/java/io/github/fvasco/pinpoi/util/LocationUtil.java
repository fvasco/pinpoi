package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import io.github.fvasco.pinpoi.BuildConfig;
import io.github.fvasco.pinpoi.PlacemarkDetailActivity;
import io.github.fvasco.pinpoi.model.PlacemarkBase;

/**
 * Location related utility
 *
 * @author Francesco Vasco
 */
public class LocationUtil {

    private static final int ADDRESS_CACHE_SIZE = 512;
    /**
     * Store resolved address
     */
    private static final LinkedHashMap<Coordinates, String> ADDRESS_CACHE = new LinkedHashMap<>(ADDRESS_CACHE_SIZE * 2, .75F, true);
    private static File addressCacheFile;
    private static Geocoder geocoder;


    private LocationUtil() {
    }

    private static synchronized void init() {
        if (addressCacheFile == null) {
            final Context applicationContext = Util.getApplicationContext();
            addressCacheFile = new File(applicationContext.getCacheDir(), "addressCache");
            if (Geocoder.isPresent()) {
                geocoder = new Geocoder(applicationContext);
            }
        }

    }

    public static Geocoder getGeocoder() {
        init();
        return geocoder;
    }

    /**
     * Get address and call optional addressConsumer in main looper
     */
    public static Future<String> getAddressStringAsync(
            final Coordinates coordinates,
            final Consumer<String> addressConsumer) {
        Util.requireNonNull(coordinates);
        return Util.EXECUTOR.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String addressString;
                init();
                synchronized (ADDRESS_CACHE) {
                    if (ADDRESS_CACHE.isEmpty()) restoreAddressCache();
                    addressString = ADDRESS_CACHE.get(coordinates);
                }
                if (addressString == null
                        // resolve geocoder
                        && geocoder != null) {
                    List<Address> addresses;
                    try {
                        addresses =
                                LocationUtil.geocoder.getFromLocation(coordinates.getLatitude(), coordinates.getLongitude(), 1);
                    } catch (Exception e) {
                        addresses = Collections.EMPTY_LIST;
                    }
                    if (!addresses.isEmpty()) {
                        addressString = LocationUtil.toString(addresses.get(0));
                        // save result in cache
                        synchronized (ADDRESS_CACHE) {
                            ADDRESS_CACHE.put(coordinates, addressString);
                            if (Thread.interrupted()) {
                                throw new InterruptedException();
                            }
                            saveAddressCache();
                        }
                    }
                }
                if (addressConsumer != null) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    final String addressStringFinal = addressString;
                    Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            addressConsumer.accept(addressStringFinal);
                        }
                    });
                }
                return addressString;
            }
        });
    }

    public static Location newLocation(double latitude, double longitude) {
        final Location location = new Location(Util.class.getSimpleName());
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(0);
        location.setTime(System.currentTimeMillis());
        return location;
    }

    /**
     * Convert an {@linkplain Address} to address string
     */
    public static String toString(Address address) {
        if (address == null) return null;

        final String separator = ", ";
        if (address.getMaxAddressLineIndex() == 0) {
            return address.getAddressLine(0);
        } else if (address.getMaxAddressLineIndex() > 0) {
            final StringBuilder stringBuilder = new StringBuilder(address.getAddressLine(0));
            for (int i = 1; i <= address.getMaxAddressLineIndex(); ++i) {
                stringBuilder.append(separator).append(address.getAddressLine(i));
            }
            return stringBuilder.toString();
        } else {
            final StringBuilder stringBuilder = new StringBuilder();
            Util.append(address.getFeatureName(), separator, stringBuilder);
            Util.append(address.getLocality(), separator, stringBuilder);
            Util.append(address.getAdminArea(), separator, stringBuilder);
            Util.append(address.getCountryCode(), separator, stringBuilder);
            return stringBuilder.length() == 0 ? address.toString() : stringBuilder.toString();
        }
    }

    /**
     * Format coordinate for GPS parser
     */
    public static String formatCoordinate(@NonNull final PlacemarkBase placemark) {
        return Location.convert(placemark.getLatitude(), Location.FORMAT_DEGREES)
                + ',' + Location.convert(placemark.getLongitude(), Location.FORMAT_DEGREES);
    }

    /**
     * Open external map app
     *
     * @param placemark       placemark to open
     * @param forceAppChooser if true show always app chooser
     */
    public static void openExternalMap(final PlacemarkBase placemark, final boolean forceAppChooser, final Context context) {
        try {
            final String coordinateFormatted = formatCoordinate(placemark);
            final Uri uri = new Uri.Builder().scheme("geo").authority(coordinateFormatted)
                    .appendQueryParameter("q", coordinateFormatted + '(' + placemark.getName() + ')')
                    .build();
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            if (forceAppChooser) {
                intent = Intent.createChooser(intent, placemark.getName());
            }
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(PlacemarkDetailActivity.class.getSimpleName(), "Error on map click", e);
            Util.showToast(e.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    private static void restoreAddressCache() {
        if (BuildConfig.DEBUG && !Thread.holdsLock(ADDRESS_CACHE)) throw new AssertionError();
        if (addressCacheFile.canRead()) {
            try {
                final DataInputStream inputStream =
                        new DataInputStream(new BufferedInputStream(new FileInputStream(addressCacheFile)));
                try {
                    // first item is entry count
                    for (int i = inputStream.readShort(); i > 0; --i) {
                        final float latitude = inputStream.readFloat();
                        final float longitude = inputStream.readFloat();
                        final String address = inputStream.readUTF();
                        ADDRESS_CACHE.put(new Coordinates(latitude, longitude), address);
                    }
                } finally {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.w(LocationUtil.class.getSimpleName(), e);
                //noinspection ResultOfMethodCallIgnored
                addressCacheFile.delete();
            }
        }
    }

    private static void saveAddressCache() {
        if (BuildConfig.DEBUG && !Thread.holdsLock(ADDRESS_CACHE)) throw new AssertionError();
        try {
            final DataOutputStream outputStream =
                    new DataOutputStream(new BufferedOutputStream(new FileOutputStream(addressCacheFile)));
            try {
                // first item is entry count
                final Iterator<Map.Entry<Coordinates, String>> iterator = ADDRESS_CACHE.entrySet().iterator();
                for (int i = ADDRESS_CACHE.size(); i > ADDRESS_CACHE_SIZE; --i) {
                    iterator.next();
                }
                outputStream.writeShort(Math.min(ADDRESS_CACHE_SIZE, ADDRESS_CACHE.size()));
                while (iterator.hasNext()) {
                    final Map.Entry<Coordinates, String> entry = iterator.next();
                    final Coordinates coordinates = entry.getKey();
                    outputStream.writeFloat(coordinates.getLatitude());
                    outputStream.writeFloat(coordinates.getLongitude());
                    outputStream.writeUTF(entry.getValue());
                }
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            Log.w(LocationUtil.class.getSimpleName(), e);
            //noinspection ResultOfMethodCallIgnored
            addressCacheFile.delete();
        }
    }
}
