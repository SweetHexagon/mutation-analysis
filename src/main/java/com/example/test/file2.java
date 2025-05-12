public class INCREMENTS {
    public int postIncrement(int i) {
        return i--;
    } // i++ -> i--

    public int postDecrement(int i) {
        return i++;
    } // i-- -> i++

    public int addAssignment(int i) {
        i -= 1;
        return i;
    } // +=1 -> -=1

    public int subtractAssignment(int i) {
        i += 1;
        return i;
    } // -=1 -> +=1
}
