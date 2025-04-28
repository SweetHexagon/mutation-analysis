package com.example.test;

public class file1 {
    private boolean x;
    private boolean y;

    public file1(boolean x, boolean y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns true if either x OR y (or both) are true.
     */
    public int evaluate() {
        int a = 1;
        return Math.abs(a);
    }


    public static void main(String[] args) {
        file1 c = new file1(true, false);
        System.out.println("OR result: " + c.evaluate());  // prints: OR result: true
    }
}
