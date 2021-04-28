/*
 * @test /nodynamiccopyright/
 * @bug 9999999
 * @summary Verify "case null" is not allowed.
 * @compile --enable-preview -source ${jdk.version} SwitchNull.java
 * @run main/othervm --enable-preview SwitchNull
 */

public class SwitchNull {
    public static void main(String... args) {
        SwitchNull instance = new SwitchNull();

        instance.run();
    }

    private void run() {
        assertEquals(0, switchIntegerBox(Integer.MIN_VALUE));
        assertEquals(1, switchIntegerBox(-2));
        assertEquals(2, switchIntegerBox(-1));
        assertEquals(3, switchIntegerBox(0));
        assertEquals(4, switchIntegerBox(1));
        assertEquals(5, switchIntegerBox(2));
        assertEquals(6, switchIntegerBox(Integer.MAX_VALUE));
        assertEquals(-1, switchIntegerBox(null));
        assertEquals(-2, switchIntegerBox(3));
        assertEquals(0, switchString(""));
        assertEquals(1, switchString("a"));
        assertEquals(2, switchString("A"));
        assertEquals(-1, switchString(null));
        assertEquals(-2, switchString("c"));
        assertEquals(0, switchEnum(E.A));
        assertEquals(1, switchEnum(E.B));
        assertEquals(2, switchEnum(E.C));
        assertEquals(-1, switchEnum(null));
        assertEquals(0, switchEnumWithDefault(E.A));
        assertEquals(1, switchEnumWithDefault(E.B));
        assertEquals(1, switchEnumWithDefault(E.C));
        assertEquals(-1, switchEnumWithDefault(null));
        testSwitchIntegerBoxExhaustive();
    }

    private int switchIntegerBox(Integer i) {
        switch (i) {
            case Integer.MIN_VALUE: return 0;
            case -2: return 1;
            case -1: return 2;
            case 0: return 3;
            case 1: return 4;
            case 2: return 5;
            case Integer.MAX_VALUE: return 6;
            case null: return -1;
            default: return -2;
        }
    }

    private int switchString(String s) {
        switch (s) {
            case "": return 0;
            case "a": return 1;
            case "A": return 2;
            case null: return -1;
            default: return -2;
        }
    }

    private int switchEnum(E e) {
        switch (e) {
            case A: return 0;
            case B: return 1;
            case C: return 2;
            case null: return -1;
        }
        throw new AssertionError(String.valueOf(e));
    }

    private int switchEnumWithDefault(E e) {
        switch (e) {
            case A: return 0;
            default: return 1;
            case null: return -1;
        }
    }

    private void testSwitchIntegerBoxExhaustive() {
        int i = Integer.MIN_VALUE;

        do {
            int result = switchIntegerBoxExhaustive(i);
            int expected = i == 0 ? 0 : 1;
            assertEquals(expected, result);
        } while (i++ < Integer.MAX_VALUE);

        int result = switchIntegerBoxExhaustive(null);
        assertEquals(-1, result);
    }

    private int switchIntegerBoxExhaustive(Integer i) {
        return switch (i) {
            case null -> -1;
            case 0 -> 0;
            default -> 1;
        };
    }

    enum E {
        A,
        B,
        C;
    }

    private void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
