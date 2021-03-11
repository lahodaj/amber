/**
 * @test
 * @compile --enable-preview -source ${jdk.version} DisambiguateAndPattern.java
 * @run main/othervm --enable-preview DisambiguateAndPattern
 */

import java.util.List;
import java.util.Objects;

public class DisambiguateAndPattern {

    public static void main(String... args) throws Throwable {
        if (!Objects.equals(4, disambiguate(new RunnableCharSequence("test")))) {
            throw new IllegalStateException();
        }
        if (!Objects.equals(-2, disambiguate("other"))) {
            throw new IllegalStateException();
        }
    }

    private static int disambiguate(Object o) throws Throwable {
        if (o instanceof CharSequence s & Runnable r) {
            return s.length();
        }
        if (o instanceof String s & true) {
            return -2;
        }
        return -1;
    }

    private static final class RunnableCharSequence implements CharSequence, Runnable {

        private final String delegate;

        public RunnableCharSequence(String delegate) {
            this.delegate = delegate;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return delegate.subSequence(end, end);
        }

        @Override
        public void run() {
        }
        
    }
}
