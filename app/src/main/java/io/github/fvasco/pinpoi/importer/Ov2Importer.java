package io.github.fvasco.pinpoi.importer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.fvasco.pinpoi.model.Placemark;

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
    private static int readIntLE(final InputStream is) throws IOException {
        return is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
    }

    @Override
    protected void importImpl(final InputStream inputStream) throws IOException {
        final DataInputStream dataInputStream = new DataInputStream(inputStream);
        byte[] nameBuffer = new byte[64];
        int b;
        while ((b = dataInputStream.read()) >= 0) {
            // if it is a simple POI record
            if (b == 2) {
                final int total = readIntLE(dataInputStream);
                final int nameLength = total - 14;

                // read lon, lat
                final float longitude = (float) readIntLE(dataInputStream) / 100000.0F;
                final float latitude = (float) readIntLE(dataInputStream) / 100000.0F;

                // read name
                if (nameLength > nameBuffer.length) {
                    //ensure buffer size
                    nameBuffer = new byte[nameLength];
                }
                dataInputStream.readFully(nameBuffer, 0, nameLength);
                // skip null byte
                if (dataInputStream.read() != 0) {
                    throw new IOException("wrong string termination " + b);
                }

                final Placemark p = new Placemark();
                p.setName(new String(nameBuffer, 0, nameLength, "ISO-8859-1"));
                p.setLongitude(longitude);
                p.setLatitude(latitude);
                importPlacemark(p);
            }
            //if it is a deleted record
            else if (b == 0) {
                dataInputStream.skip(9);
            }
            //if it is a skipper record
            else if (b == 1) {
                dataInputStream.skip(20);
            } else {
                throw new IOException("wrong record type " + b);
            }
        }
    }
}
