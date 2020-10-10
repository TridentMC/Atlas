package com.tridevmc.atlas.util;

/**
 * A simple result type, for encapsulating return values where an error may be
 * encountered, and a value may beunable to be retrieved.
 *
 * @param <T> The type of the value
 * @param <E> The type of the Exception
 */
public class Result<T, E extends Exception> {
    private final T value;
    private final E error;

    public Result(T value) {
        this.value = value;
        this.error = null;
    }

    public Result(E error) {
        this.value = null;
        this.error = error;
    }

    public boolean isError() {
        return this.error != null;
    }

    public boolean isOk() {
        return this.value != null;
    }

    public T getValue() {
        return value;
    }

    public E getError() {
        return error;
    }
}