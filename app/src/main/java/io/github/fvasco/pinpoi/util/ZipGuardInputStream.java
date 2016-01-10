package io.github.fvasco.pinpoi.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

/**
 * Filter input stream to close all, this stream close only current entry;
 *
 * @author Francesco Vasco
 */
public class ZipGuardInputStream extends FilterInputStream {

    public ZipGuardInputStream(ZipInputStream in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        ((ZipInputStream) this.in).closeEntry();
    }
}
