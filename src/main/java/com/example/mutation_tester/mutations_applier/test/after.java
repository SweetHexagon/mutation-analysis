package com.example.mutation_tester.mutations_applier.test;

public class ExampleLoop {

    public static void main(String[] args) {
        System.out.println("Called from test: " + java.util.Arrays.stream(Thread.currentThread().getStackTrace()).filter(e -> e.getClassName().contains("Test")).findFirst().map(e -> e.getClassName() + "#" + e.getMethodName()).orElse("Unknown"));
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                System.out.println("Breaking the loop at i = " + i);
                continue;
            }
            System.out.println("Current value of i: " + i);
        }
        System.out.println("Loop finished.");
        int count = 0;
        while (true) {
            count++;
            if (count > 3) {
                // Infinite loop do not replace
                break;
            }
            System.out.println("Count: " + count);
        }
        System.out.println("While loop finished.");
    }
}
