package com.example;

import java.util.List;
import java.util.Arrays;

public class ListFactory {
    // a perfectly valid generic varargs helper
    public static <T> List<T> of(T... elements) {
        // trivial implementation
        return Arrays.asList(elements);
    }
}
