/**
 * @test
 * @compile PatternMatchingCase.java
 * @run main PatternMatchingCase
 */

import com.sun.source.tree.CaseLabelTree;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

public class PatternMatchingCase {
    public static void main(String... args) throws IOException {
        caseTest();
    }

    private static void caseTest() throws IOException {
        String testCode = """
                          package test;
                          public class Test {
                              private void t(Object o) {
                                  int r = 0;
                                  switch (o) {
                                      case E.A -> r = 0;
                                      case String s when s.isEmpty() -> r = 1;
                                      default -> r = -1;
                                  };
                                  switch (o) {
                                      case E.A: r = 0; break;
                                      case String s when s.isEmpty(): r = 1; break;
                                      default: r = -1; break;
                                  };
                              }
                              enum E {A}
                          }
                          """;
        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, List.of(SimpleJavaFileObject.forSource(URI.create("mem:///Test.java"), testCode)));
        CompilationUnitTree cut = task.parse().iterator().next();

        new TreeScanner<Void, Void>() {
            public Void visitSwitch(SwitchTree tree, Void v) {
                for (CaseTree ct : tree.getCases()) {
                    System.err.println("case: " + ct);
                    switch (ct) {
                        case CaseTree(ExpressionTree expression, List<? extends StatementTree> statements) -> {
                            System.err.println("expression=" + expression + ", statements=" + statements);
                        }
                        default -> System.err.println("default");
                    }
                    switch (ct) {
                        case CaseTree(List<? extends ExpressionTree> expressions, List<? extends StatementTree> statements) -> {
                            System.err.println("expressions=" + expressions + ", statements=" + statements);
                        }
                        default -> System.err.println("default");
                    }
                    switch (ct) {
                        case CaseTree(List<? extends ExpressionTree> expressions, Tree body) -> {
                            System.err.println("expressions=" + expressions + ", body=" + body);
                        }
                        default -> System.err.println("default");
                    }
                    switch (ct) {
                        case CaseTree(List<? extends CaseLabelTree> labels, List<? extends StatementTree> statements, ExpressionTree guard) -> {
                            System.err.println("labels=" + labels + ", statements=" + statements + ", guard=" + guard);
                        }
                        default -> System.err.println("default");
                    }
                    switch (ct) {
                        case CaseTree(List<? extends CaseLabelTree> labels, Tree body, ExpressionTree guard) -> {
                            System.err.println("labels=" + labels + ", body=" + body + ", guard=" + guard);
                        }
                        default -> System.err.println("default");
                    }
                }
                return null;
            }
        }.scan(cut, null);
    }
}
