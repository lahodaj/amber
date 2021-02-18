/**
 * @test
 * @modules jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 */

import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.util.Context;
import java.nio.charset.Charset;

public class DisambiguateAndPatternExhaustive {

    public static void main(String... args) throws Throwable {
        DisambiguateAndPatternExhaustive test = new DisambiguateAndPatternExhaustive();
        test.disambiguationTest("o instanceof String s & s.length() == 0",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("o instanceof String s & true(s.length() == 0)",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("o instanceof String s & method(call)",
                                 ExpressionType.EXPRESSION);
//        test.disambiguationTest("o instanceof String s & deconstruction(pattern val)",
//                                 ExpressionType.PATTERN);
        test.disambiguationTest("o instanceof String s & type pattern",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("o instanceof CharSequence cs & cs instanceof String s",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("o instanceof String s & type[] pattern",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("o instanceof String s & type[0]",
                                 ExpressionType.EXPRESSION);
//        test.disambiguationTest("o instanceof String[] s & {String s1, ...}",
//                                 ExpressionType.PATTERN);
    }

    private final ParserFactory factory;

    public DisambiguateAndPatternExhaustive() {
        Context context = new Context();
        JavacFileManager jfm = new JavacFileManager(context, true, Charset.defaultCharset());
        factory = ParserFactory.instance(context);
    }

    void disambiguationTest(String code, ExpressionType expectedType) {
        JavacParser parser = factory.newParser(code, false, false, false);
        ExpressionTree result = parser.parseExpression();
        ExpressionType actualType = switch (result.getKind()) {
            case INSTANCE_OF -> ExpressionType.PATTERN;
            case AND -> ExpressionType.EXPRESSION;
            default -> throw new AssertionError("Unexpected result: " + result);
        };
        if (expectedType != actualType) {
            throw new AssertionError("Expected: " + expectedType + ", actual: " + actualType +
                                      ", for: " + code + ", parsed: " + result);
        }
    }

    enum ExpressionType {
        PATTERN,
        EXPRESSION;
    }

}
