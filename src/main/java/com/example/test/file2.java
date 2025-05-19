package com.example;

import java.util.List;

public class ListFactory {
    // illegal: 'transient' may not appear on a method,
    // and E is undeclared here → Spoon/javac will mark E as missing
    public static transient List<E> of(E[] elements) {
        return null;
    }
}
