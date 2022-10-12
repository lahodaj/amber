/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary TODO
 * @library /tools/lib/types
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.file
 * @run main InferenceUnitTest
 */

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import java.util.ArrayList;
import java.util.List;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class InferenceUnitTest {

    JavacTaskImpl task;
    Types types;

    public static void main(String... args) throws Exception {
        new InferenceUnitTest().runAll();
    }

    void runAll() throws URISyntaxException {
        task = (JavacTaskImpl) ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, List.of(new SimpleJavaFileObject(new URI("mem://Test.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return """
                                       interface A<T> {}
                                       interface B<T> extends A<T> {}
                                       interface C<X,Y> extends A<X> {}
                                       interface D<X,Y> extends A<Y> {}
                                       interface E<T> extends C<T,T> {}
                                       interface F<T> extends A<B<T>> {}
                                       interface G<T extends Number> extends A<T> {}
                                       interface H extends A<String> {}
                                       interface I<T> extends H {}
                                       """;
            }
        }));
        task.enter();
        types = Types.instance(task.getContext());

        checkAsSub("A<String>", "B", "B<java.lang.String>");
        checkAsSub("A<String>", "C", "C<java.lang.String,?>");
        checkAsSub("A<String>", "D", "D<?,java.lang.String>");
        checkAsSub("A<String>", "E", "E<java.lang.String>");
        checkAsSub("A<String>", "F", null);
//        checkAsSub("A<String>", "G", null); // doesn't check bounds
        checkAsSub("A<String>", "H", "H");
        checkAsSub("A<String>", "I", "I<?>");

        checkAsSub("A<B<String>>", "B", "B<B<java.lang.String>>");
        checkAsSub("A<B<String>>", "C", "C<B<java.lang.String>,?>");
        checkAsSub("A<B<String>>", "F", "F<java.lang.String>");
        checkAsSub("A<B<String>>", "H", null);
        checkAsSub("A<B<String>>", "I", null);

        checkAsSub("C<String, String>", "E", "E<java.lang.String>");
        checkAsSub("C<String, Integer>", "E", null);
        checkAsSub("C<A<?>, A<?>>", "E", "E<A<?>>");
        checkAsSub("C<A<? extends Object>, A<?>>", "E", "E<A<? extends java.lang.Object>>"); //TODO: was: E<A<?>>

        if (false) {
        //TODO: raw types?
        checkAsSub("A", "B", "B");
        checkAsSub("A", "C", "C");
        checkAsSub("A", "D", "D");
        checkAsSub("A", "E", "E");
        checkAsSub("A", "F", "F");
        checkAsSub("A", "G", "G");
        checkAsSub("A", "H", "H");
        }

        checkAsSub("A", "I", "I<?>"); // always erases if input is raw

        checkAsSub("A<?>", "B", "B<?>");
        checkAsSub("A<?>", "C", "C<?,?>");
        checkAsSub("A<?>", "D", "D<?,?>");
        checkAsSub("A<?>", "E", "E<?>");
        checkAsSub("A<?>", "F", "F<?>");
        checkAsSub("A<?>", "G", "G<?>");
        checkAsSub("A<?>", "H", "H");
        checkAsSub("A<?>", "I", "I<?>");
        
        checkAsSub("A<? extends Runnable>", "B", "B<? extends java.lang.Runnable>");
        checkAsSub("A<? extends Runnable>", "C", "C<? extends java.lang.Runnable,?>");
        checkAsSub("A<? extends Runnable>", "D", "D<?,? extends java.lang.Runnable>");
        checkAsSub("A<? extends Runnable>", "E", "E<? extends java.lang.Runnable>");
        checkAsSub("A<? extends Runnable>", "F", null);
        checkAsSub("A<? extends Runnable>", "G", "G<? extends java.lang.Number&java.lang.Runnable>"); // should infer an intersection bound
        checkAsSub("A<? extends Runnable>", "H", null);
        checkAsSub("A<? extends Runnable>", "I", null);

        checkAsSub("A<? extends B<String>>", "F", "F<java.lang.String>"); // inference doesn't recur on bounds checks; see XXX in InjectBounds.visitClassType
        checkAsSub("A<? extends A<String>>", "F", "F<java.lang.String>"); // inference doesn't recur on bounds checks

        checkAsSub("C<? extends Number, Integer>", "E", "E<java.lang.Integer>"); // doesn't know how to mix types and wildcards
        checkAsSub("C<Integer, ? extends Number>", "E", "E<java.lang.Integer>"); // doesn't know how to mix types and wildcards
        checkAsSub("C<?, ? extends Number>", "E", "E<? extends java.lang.Number>");
        checkAsSub("C<? extends Number, ?>", "E", "E<? extends java.lang.Number>");
        
        checkAsSub("C<? extends Number, ? extends Integer>", "E", "E<? extends java.lang.Integer>");
        checkAsSub("C<? extends Integer, ? extends Number>", "E", "E<? extends java.lang.Integer>");
        checkAsSub("C<? extends Runnable, ? extends Cloneable>", "E", "E<? extends java.lang.Object&java.lang.Cloneable&java.lang.Runnable>"); // should infer an intersection bound
        checkAsSub("C<? extends Number, ? super Integer>", "E", "E<? extends java.lang.Number>"); // doesn't know how to mix lower/upper
        checkAsSub("C<? super Integer, ? super Number>", "E", "E<? super java.lang.Number>");
        checkAsSub("C<? super B<String>, ? super C<String,String>>", "E", "E<? super A<java.lang.String>>"); // doesn't do lub

        checkAsSub("H", "I", "I<?>");

        checkAsSub("B<String>", "C", "C<java.lang.String,?>"); // no sideways casts

        //TODO: tests that use type variables!
    }

    private void checkAsSub(String base, String test, String expected) {
        Type baseType = parseType(base);
        TypeSymbol testType = parseType(test).tsym;
        Type actualType = types.infer(baseType, testType);
        String actualTypeString = actualType != null ? actualType.toString() : null;
//        Type expectedType = expected != null ? strToTypeFactory.getType(expected) : null;
        if (!Objects.equals(expected, actualTypeString)) {
            error("Unexpected type, expected: " + expected + ", got: " + actualTypeString);
        }
    }
    Type parseType(String spec) {
        Context context = task.getContext();
        ParserFactory fact = ParserFactory.instance(context);
        JCExpression specTypeTree = fact.newParser(spec, false, false, false).parseType();
        Attr attr = Attr.instance(context);
        Env<AttrContext> env = Enter.instance(context).getEnvs().iterator().next();
        return attr.attribType(specTypeTree, env);
    }

    /** assert that 's' is the same type as 't' */
    public void assertSameType(Type s, Type t) {
        assertSameType(s, t, true);
    }

    /** assert that 's' is/is not the same type as 't' */
    public void assertSameType(Type s, Type t, boolean expected) {
        if (types.isSameType(s, t) != expected) {
            String msg = expected ?
                " is not the same type as " :
                " is the same type as ";
            error(s + msg + t);
        }
    }

    private void error(String msg) {
        throw new AssertionError("Unexpected result: " + msg);
    }

}
