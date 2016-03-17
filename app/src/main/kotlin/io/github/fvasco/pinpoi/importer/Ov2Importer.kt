package io.github.fvasco.pinpoi.importer

import android.util.Log
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.util.Coordinates
import io.github.fvasco.pinpoi.util.Util
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Tomtom OV2 importer

 * @author Francesco Vasco
 */
/* File format
1 byte: recotd type
4 bytes: length of this record in bytes (including the T and L fields)
4 bytes: longitude coordinate of the POI
4 bytes: latitude coordinate of the POI
length-14 bytes: ASCII string specifying the name of the POI
1 byte: null byte
*/
class Ov2Importer : AbstractImporter() {
    private fun readIntLE(inputStream: InputStream): Int {
        return inputStream.read() or (inputStream.read() shl 8) or (inputStream.read() shl 16) or (inputStream.read() shl 24)
    }

    @Throws(IOException::class)
    override fun importImpl(inputStream: InputStream) {
        val dataInputStream = DataInputStream(inputStream)
        var nameBuffer = ByteArray(64)
        var rectype = dataInputStream.read()
        while (rectype >= 0) {
            when (rectype) {

            // it is a simple POI record
                2, 3 -> {
                    val total = readIntLE(dataInputStream)
                    Log.i(Ov2Importer::class.java.simpleName, "Process record type $rectype total $total")
                    var nameLength = total - 14

                    // read lon, lat
                    // coordinate format: int*100000
                    val longitudeInt = readIntLE(dataInputStream)
                    val latitudeInt = readIntLE(dataInputStream)
                    if (longitudeInt < -18000000 || longitudeInt > 18000000
                            || latitudeInt < -9000000 || latitudeInt > 9000000) {
                        throw IOException("Wrong coordinates $longitudeInt,$latitudeInt")
                    }

                    // read name
                    if (nameLength > nameBuffer.size) {
                        //ensure buffer size
                        nameBuffer = ByteArray(nameLength)
                    }
                    dataInputStream.readFully(nameBuffer, 0, nameLength)
                    // skip null byte
                    if (dataInputStream.read() != 0) {
                        throw IOException("wrong string termination " + rectype)
                    }
                    // if rectype=3 description contains two-zero terminated string
                    // select first, discard other
                    if (rectype == 3) {
                        var i = 0
                        while (i < nameLength) {
                            if (nameBuffer[i].toInt() == 0) {
                                // set name length
                                // then exit
                                nameLength = i
                            }
                            ++i
                        }
                    }
                    val placemark = Placemark()
                    placemark.name = TextImporter.toString(nameBuffer, 0, nameLength)
                    placemark.coordinates = Coordinates(latitudeInt / 100000f, longitudeInt / 100000f)
                    importPlacemark(placemark)
                }

            // block header
                1 -> {
                    Log.i(Ov2Importer::class.java.simpleName, "Skip record type " + rectype)
                    dataInputStream.skipBytes(20)
                }

                0, 100, // deleted
                9, 25// other type
                -> {
                    val total = readIntLE(dataInputStream)
                    Log.i(Ov2Importer::class.java.simpleName, "Skip record type $rectype total $total")
                    dataInputStream.skipBytes( total - 4)
                }

                else -> throw IOException("Unknown record " + rectype)
            }
            rectype = dataInputStream.read()
        }
    }
}
