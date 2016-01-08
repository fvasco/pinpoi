package io.github.fvasco.pinpoi.importer;

import java.util.ArrayList;

import io.github.fvasco.pinpoi.util.Consumer;

/**
 * Add element to list.
 *
 * @author Francesco Vasco
 */
public class ListConsumer<T> extends ArrayList<T> implements Consumer<T> {
    @Override
    public void accept(T elem) {
        add(elem);
    }
}
