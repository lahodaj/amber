/*
 * @test /nodynamiccopyright/
 * @summary XXX
 * @compile NullSwitch.java
 * @run main NullSwitch
 */

public class NullSwitch {

    public static void main(String[] args) {
        new NullSwitch().switchTest();
    }
    
    void switchTest() {
        assertEquals(0, matchingSwitch1(""));
        assertEquals(1, matchingSwitch1("a"));
        assertEquals(100, matchingSwitch1(0));
        assertEquals(-1, matchingSwitch1(null));
        assertEquals(-2, matchingSwitch1(0.0));
        assertEquals(0, matchingSwitch2(""));
        assertEquals(1, matchingSwitch2(null));
        assertEquals(1, matchingSwitch2(0.0));
    }

    private int matchingSwitch1(Object obj) {
        return switch (obj) {
            case String s -> s.length();
            case null, Integer i -> i == null ? -1 : 100 + i;
            default -> -2;
        };
    }

    private int matchingSwitch2(Object obj) {
        return switch (obj) {
            case String s -> 0;
            case null, default -> 1;
        };
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

}
