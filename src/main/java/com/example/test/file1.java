package com.example;

import java.util.List;
import java.util.Arrays;

public class ListFactory {
    public <T extends CustomerRole> Optional<T> instance() {
        var typeCst = this.typeCst;
        try {
            return (Optional<T>) Optional.of(typeCst.getDeclaredConstructor().newInstance());
        } catch (InstantiationException
                 | IllegalAccessException
                 | NoSuchMethodException
                 | InvocationTargetException e) {
            logger.error("error creating an object", e);
        }
        return Optional.empty();
    }
}
