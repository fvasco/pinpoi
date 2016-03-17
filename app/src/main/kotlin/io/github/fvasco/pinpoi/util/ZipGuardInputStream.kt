package io.github.fvasco.pinpoi.util

import java.io.FilterInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Filter input stream to close all, this stream close only current entry;

 * @author Francesco Vasco
 */
class ZipGuardInputStream(`in`: ZipInputStream) : FilterInputStream(`in`) {

    @Throws(IOException::class)
    override fun close() {
        (this.`in` as ZipInputStream).closeEntry()
    }
}
