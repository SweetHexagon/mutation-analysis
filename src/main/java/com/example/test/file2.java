public class EXPERIMENTAL_NAKED_RECEIVER {

    // 1) naked receiver
    public BigInteger getAbsolute(BigInteger a) {
        return a;
    }

    // 2) naked receiver
    public String trimmed(String s) {
        return s;
    }

    // 3) naked receiver
    public Object toStr(Object o) {
        return o;
    }

    // 4) naked receiver
    public List<String> subList(List<String> list) {
        return list;
    } // mutated to return receiver instead of calling method
}
