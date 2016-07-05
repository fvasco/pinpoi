package io.github.fvasco.pinpoi.importer

import android.util.Log
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.util.Coordinates
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern

/**
 * Import ASCII/UTF-8 file in format
 *
 *  * ASC: `longitude latitude "POI name"`
 *  * CSV: `longitude, latitude, "POI name"`
 *

 * @author Francesco Vasco
 */
class TextImporter : AbstractImporter() {

    private val buffer = ByteArray(4 * 1024)

    @Throws(IOException::class)
    private fun readLine(inputStream: InputStream): String? {
        var pos = 0
        var b: Int = inputStream.read()
        while (b >= 0) {
            if (b.toChar() == '\n' || b.toChar() == '\r') {
                if (pos > 0) {
                    return toString(buffer, 0, pos)
                }
            } else {
                if (pos == buffer.size) {
                    throw IOException("Line too long")
                }
                buffer[pos] = b.toByte()
                ++pos
            }
            b = inputStream.read()
        }
        return if (b < 0 && pos == 0) null else toString(buffer, 0, pos)
    }


    @Throws(IOException::class)
    override fun importImpl(inputStream: InputStream) {
        var line: String? = readLine(inputStream)
        while (line != null) {
            val matcher = LINE_PATTERN.matcher(line)
            if (matcher.matches()) {
                try {
                    val placemark = Placemark(
                            // remove double double-quotes
                            name = matcher.group(6).replace("\"\"", "\""),
                            coordinates = Coordinates(java.lang.Float.parseFloat(matcher.group(2)), java.lang.Float.parseFloat(matcher.group(4)))
                    )
                    importPlacemark(placemark)
                } catch (nfe: NumberFormatException) {
                    Log.d(TextImporter::class.java.simpleName, "Skip line: " + line, nfe)
                }

            } else {
                Log.d(TextImporter::class.java.simpleName, "Skip line: " + line)
            }
            line = readLine(inputStream)
        }
    }

    companion object {

        private val LINE_PATTERN = Pattern.compile("\\s*(\"?)([+-]?\\d+\\.\\d+)\\1[,;\\s+](\"?)([+-]?\\d+\\.\\d+)\\3[,;\\s+](\"?)(.*)\\5\\s*")
        private val UTF_8_DECODER = Charsets.UTF_8.newDecoder()
        private val LATIN1_DECODER = Charsets.ISO_8859_1.newDecoder()

        init {
            // fails on not mappable characters
            UTF_8_DECODER.onUnmappableCharacter(CodingErrorAction.REPORT)
        }

        /**
         * Decode text, if UTF-8 fails then use ISO-8859-1
         */
        fun toString(byteBuffer: ByteArray, start: Int, len: Int): String =
            try {
                UTF_8_DECODER.decode(ByteBuffer.wrap(byteBuffer, start, len)).toString()
            } catch (e: CharacterCodingException) {
                LATIN1_DECODER.decode(ByteBuffer.wrap(byteBuffer, start, len)).toString()
            }
    }
}
