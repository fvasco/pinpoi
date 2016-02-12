package io.github.fvasco.pinpoi.importer;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.util.Util;

/**
 * Tomtom OV2 importer
 *
 * @author Francesco Vasco
 */
/* File format
1 byte: type (always 2)
4 bytes: length of this record in bytes (including the T and L fields)
4 bytes: longitude coordinate of the POI
4 bytes: latitude coordinate of the POI
length-14 bytes: ASCII string specifying the name of the POI
1 byte: null byte
 */
public class Ov2Importer extends AbstractImporter {
    private static final CharsetDecoder UTF_8_DECODER = Charset.forName("UTF-8").newDecoder();
    private static final CharsetDecoder ISO8859_1_DECODER = Charset.forName("ISO-8859-1").newDecoder();

    static {
        UTF_8_DECODER.onUnmappableCharacter(CodingErrorAction.REPORT);
        ISO8859_1_DECODER.onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    private static int readIntLE(final InputStream is) throws IOException {
        return is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
    }

    private static String toString(byte[] byteBuffer, int len) {
        try {
            return UTF_8_DECODER.decode(ByteBuffer.wrap(byteBuffer, 0, len)).toString();
        } catch (CharacterCodingException e) {
            try {
                return new String(byteBuffer, 0, len, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                return new String(byteBuffer, 0, len);
            }
        }
    }

    @Override
    protected void importImpl(@NonNull final InputStream inputStream) throws IOException {
        final DataInputStream dataInputStream = new DataInputStream(inputStream);
        byte[] nameBuffer = new byte[64];
        for (int rectype = dataInputStream.read(); rectype >= 0; rectype = dataInputStream.read()) {
            // it is a simple POI record
            if (rectype == 2 || rectype == 3) {
                final int total = readIntLE(dataInputStream);
                Log.i(Ov2Importer.class.getSimpleName(), "Process record type " + rectype + " total " + total);
                int nameLength = total - 14;

                // read lon, lat
                // coordinate format: int*100000
                final int longitudeInt = readIntLE(dataInputStream);
                final int latitudeInt = readIntLE(dataInputStream);
                if (longitudeInt < -18000000 || longitudeInt > 18000000
                        || latitudeInt < -9000000 || latitudeInt > 9000000) {
                    throw new IOException("Wrong coordinate " +
                            longitudeInt + ',' + latitudeInt);
                }

                // read name
                if (nameLength > nameBuffer.length) {
                    //ensure buffer size
                    nameBuffer = new byte[nameLength];
                }
                dataInputStream.readFully(nameBuffer, 0, nameLength);
                // skip null byte
                if (dataInputStream.read() != 0) {
                    throw new IOException("wrong string termination " + rectype);
                }
                // if rectype=3 description contains two-zero terminated string
                // select first
                if (rectype == 3) {
                    int i = 0;
                    while (i < nameLength) {
                        if (nameBuffer[i] == 0) {
                            // set name length
                            // then exit
                            nameLength = i;
                        }
                        ++i;
                    }
                }
                final Placemark p = new Placemark();
                p.setName(toString(nameBuffer, nameLength));
                p.setLongitude(longitudeInt / 100000F);
                p.setLatitude(latitudeInt / 100000F);
                importPlacemark(p);
            } else if (rectype == 1) {
                // block header
                Log.i(Ov2Importer.class.getSimpleName(), "Skip record type " + rectype);
                Util.skip(dataInputStream, 20);
            } else if (rectype == 0 || rectype == 100// deleted
                    || rectype == 9 || rectype == 25// other type
                    ) {
                final int total = readIntLE(dataInputStream);
                Log.i(Ov2Importer.class.getSimpleName(), "Skip record type " + rectype + " total " + total);
                Util.skip(dataInputStream, total - 4);
            } else {
                throw new IOException("Unknown record " + rectype);
            }
        }
    }
}
