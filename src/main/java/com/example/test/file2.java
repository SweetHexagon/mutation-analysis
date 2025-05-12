public class VOID_METHOD_CALLS {
    private int count = 0;

    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }

    public void test() { /* void method call removed */ }                // remove void method call
}
