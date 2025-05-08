public class test {
    public void someVoidMethod(int i) {
        // does something
    }

    public void foo() {
        someVoidMethod(i);
        System.out.println(true || true);
    }
}