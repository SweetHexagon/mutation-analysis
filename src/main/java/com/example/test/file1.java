public class EXPERIMENTAL_NAKED_RECEIVER {

    // 1) BigInteger.abs() ➝ a.a.abs()
    public BigInteger getAbsolute(BigInteger a) {
        return a.abs();
    }

    // 2) String.trim() ➝ s.trim()
    public String trimmed(String s) {
        return s.trim();
    }

    // 3) Object.toString() ➝ o.toString()
    public Object toStr(Object o) {
        return o.toString();
    }

    // 4) List.subList(0,1) ➝ list.subList(0,1)
    public List<String> subList(List<String> list) {
        return list.subList(0, 1);
    }
}
