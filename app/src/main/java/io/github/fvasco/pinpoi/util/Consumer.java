package io.github.fvasco.pinpoi.util;

/**
 * A generic consumer interface.
 *
 * @author Francesco Vasco
 */
public interface Consumer<T> {

    /**
     * Performs this operation on the given argument.
     *
     * @param elem element to consume.
     */
    void accept(T elem);
}
