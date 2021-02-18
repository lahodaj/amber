
import java.util.Objects;
import java.util.function.Function;

/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 9999999
 * @summary XXX
 * @compile --enable-preview -source ${jdk.version} Guards.java
 * @run main/othervm --enable-preview Guards
 */
public class Guards {
    public static void main(String... args) {
        new Guards().run();
    }

    void run() {
        run(this::typeTestPatternSwitchTest);
//        run(this::typeTestPatternSwitchExpressionTest);
//        run(this::testBooleanSwitchExpression);
    }

    void run(Function<Object, String> convert) {
        assertEquals("zero", convert.apply(0));
        assertEquals("one", convert.apply(1));
        assertEquals("other", convert.apply(-1));
        assertEquals("any", convert.apply(""));
    }

    String typeTestPatternSwitchTest(Object o) {
        switch (o) {
            case Integer i & true(i == 0): return "zero";
            case Integer i & true(i == 1): return "one";
            case Integer i: return "other";
            case Object x: return "any";
            default: throw new IllegalStateException("TODO - needed?");
        }
    }

    String typeTestPatternSwitchExpressionTest(Object o) {
        return switch (o) {
            case Integer i & true(i == 0) -> "zero";
            case Integer i & true(i == 1) -> { yield "one"; }
            case Integer i -> "other";
            case Object x -> "any";
            default -> throw new IllegalStateException("TODO - needed?");
        };
    }

    String testBooleanSwitchExpression(Object o) {
        String x;
        if (switch (o) {
            case Integer i & true(i == 0) -> (x = "zero") != null;
            case Integer i & true(i == 1) -> { x = "one"; yield true; }
            case Integer i -> { x = "other"; yield true; }
            case Object other -> (x = "any") != null;
            default -> false;
        }) {
            return x;
        } else {
            throw new IllegalStateException("TODO - needed?");
        }
    }

    void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
