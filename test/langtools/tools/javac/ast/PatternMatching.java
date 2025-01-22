/**
 * @test
 * @compile PatternMatching.java
 * @run main PatternMatching
 */

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

public class PatternMatching {
    public static void main(String... args) throws IOException {
        String testCode = """
                          package test;
                          public class Test {
                              private void t(int i) {
                                  int j;
                                  i = i + 1;
                                  i = i;
                                  this.i = i;
                                  i = this.i;
                                  this.i = this.i;
                                  Test test = new Test();
                                  i = test.i;
                                  test.i = test.i;
                              }
                          }
                          """;
        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, List.of(SimpleJavaFileObject.forSource(URI.create("mem:///Test.java"), testCode)));
        CompilationUnitTree cut = task.parse().iterator().next();

        analyzeAssignments(task, new TreePath(cut));
    }

    private static void analyzeAssignments(JavacTask task, TreePath path) {
        switch (path.getLeaf()) {
            case AssignmentTree(ExpressionTree variable, ExpressionTree expression) -> { //it would be nice if there would be a way to get TreePaths out of this - named patterns (on TreePath), pattern parameters (for patter on AssignmentTree), etc.
                TreePath variablePath = new TreePath(path, variable);
                TreePath expressionPath = new TreePath(path, expression);

                if (representsTheSameObject(task, variablePath, expressionPath)) {
                    System.err.println("found useless assignment: " + path.getLeaf());
                }

                analyzeAssignments(task, variablePath);
                analyzeAssignments(task, expressionPath);
            }
            case Tree(Tree[] children) -> Arrays.stream(children).forEach(c -> analyzeAssignments(task, new TreePath(path, c)));
        }
    }

    private static boolean representsTheSameObject(JavacTask task, TreePath first, TreePath second) {
        Trees trees = Trees.instance(task);

        return switch (first.getLeaf()) {
            case IdentifierTree _ when second.getLeaf() instanceof IdentifierTree _ &&
                                       Objects.equals(trees.getElement(first), trees.getElement(second))
                                  -> true;
            case IdentifierTree _ when second.getLeaf() instanceof MemberSelectTree(IdentifierTree(var selector), _) &&
                                       "this".contentEquals(selector) &&
                                        Objects.equals(trees.getElement(first), trees.getElement(second))
                                  -> true;
            case MemberSelectTree(IdentifierTree(var selector), _) when second.getLeaf() instanceof IdentifierTree _ &&
                                                                        "this".contentEquals(selector) &&
                                                                         Objects.equals(trees.getElement(first), trees.getElement(second))
                                                                   -> true;
            case MemberSelectTree(var firstSelector, _) when second.getLeaf() instanceof MemberSelectTree(var secondSelector, _) &&
                                                             representsTheSameObject(task, new TreePath(first, firstSelector), new TreePath(second, secondSelector)) &&
                                                             Objects.equals(trees.getElement(first), trees.getElement(second))
                                                        -> true; //it would be good to be able to deconstruct to TreePath - named patterns (on TreePath), or pattern parameters
            default -> false;
        };
    }

}
