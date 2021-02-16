/**
 * @test
 * @compile --enable-preview -source ${jdk.version} DisambiguateAndPattern.java
 * @run main/othervm --enable-preview DisambiguateAndPattern
 */

import java.util.List;
import java.util.Objects;

public class DisambiguateAndPattern {

    public static void main(String... args) throws Throwable {
        if (!Objects.equals(4, disambiguate("test"))) {
            throw new IllegalStateException();
        }
        if (!Objects.equals(4, disambiguate("TEST"))) {
            throw new IllegalStateException();
        }
        if (!Objects.equals(-2, disambiguate("other"))) {
            throw new IllegalStateException();
        }
    }

    private static int disambiguate(Object o) throws Throwable {
        if (o instanceof String s & true(s.equalsIgnoreCase("test"))) {
            return s.length();
        }
        if (o instanceof String s & true) {
            return -2;
        }
        return -1;
    }

}
