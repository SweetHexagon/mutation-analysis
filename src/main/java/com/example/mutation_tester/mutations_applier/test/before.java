package com.example.mutation_tester.mutations_applier.test;

public class ExampleLoop {
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                System.out.println("Breaking the loop at i = " + i);
                break; // This break statement will be replaced
            }
            System.out.println("Current value of i: " + i);
        }
        System.out.println("Loop finished.");

        int count = 0;
        while (true) {
            count++;
            if (count > 3) {
                break; // Another break to be replaced
            }
            System.out.println("Count: " + count);
        }
        System.out.println("While loop finished.");
    }
}
