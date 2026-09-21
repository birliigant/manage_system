package org.example.db;

@FunctionalInterface
public interface SqlConsumer<T> {
    void accept(T value) throws Exception;
}
