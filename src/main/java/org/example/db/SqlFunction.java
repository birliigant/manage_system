package org.example.db;

@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T value) throws Exception;
}
