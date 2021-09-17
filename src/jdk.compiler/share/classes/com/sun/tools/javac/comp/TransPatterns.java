/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac.comp;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CaseTree.CaseKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Kinds.Kind;
import static com.sun.tools.javac.code.Kinds.Kind.MTH;
import static com.sun.tools.javac.code.Kinds.Kind.VAR;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.BindingSymbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodHandleSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCGuardPattern;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCBindingPattern;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.TypeTag;
import java.util.Map;
import java.util.Map.Entry;
import java.util.LinkedHashMap;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.RecordComponent;
import com.sun.tools.javac.code.Type;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.code.TypeTag.CHAR;
import static com.sun.tools.javac.code.TypeTag.INT;
import static com.sun.tools.javac.code.TypeTag.SHORT;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayPattern;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCDeconstructionPattern;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCGuardPattern;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCParenthesizedPattern;
import com.sun.tools.javac.tree.JCTree.JCPattern;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import com.sun.tools.javac.tree.JCTree.LetExpr;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import java.util.Iterator;

/**
 * This pass translates pattern-matching constructs, such as instanceof <pattern>.
 */
public class TransPatterns extends TreeTranslator {

    protected static final Context.Key<TransPatterns> transPatternsKey = new Context.Key<>();

    public static TransPatterns instance(Context context) {
        TransPatterns instance = context.get(transPatternsKey);
        if (instance == null)
            instance = new TransPatterns(context);
        return instance;
    }

    private final Symtab syms;
    private final Attr attr;
    private final Resolve rs;
    private final Types types;
    private final Operators operators;
    private final Names names;
    private final Target target;
    private final Preview preview;
    private TreeMaker make;
    private Env<AttrContext> env;

    BindingContext bindingContext = new BindingContext() {
        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            return null;
        }

        @Override
        VarSymbol getBindingFor(BindingSymbol varSymbol) {
            return null;
        }

        @Override
        Map<BindingSymbol, JCVariableDecl> bindingVars(int diagPos) {
            return Collections.emptyMap();
        }

        @Override
        JCStatement decorateStatement(JCStatement stat) {
            return stat;
        }

        @Override
        JCExpression decorateExpression(JCExpression expr) {
            return expr;
        }

        @Override
        BindingContext pop() {
            //do nothing
            return this;
        }

        @Override
        boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
            return false;
        }
    };

    JCLabeledStatement pendingMatchLabel = null;

    boolean debugTransPatterns;

    private ClassSymbol currentClass = null;
    private List<JCTree> condyableMethods = List.nil();
    private MethodSymbol currentMethodSym = null;
    private VarSymbol currentValue = null;

    protected TransPatterns(Context context) {
        context.put(transPatternsKey, this);
        syms = Symtab.instance(context);
        attr = Attr.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        operators = Operators.instance(context);
        names = Names.instance(context);
        target = Target.instance(context);
        preview = Preview.instance(context);
        debugTransPatterns = Options.instance(context).isSet("debug.patterns");
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        if (tree.pattern instanceof JCPattern) {
            //E instanceof $pattern
            //=>
            //(let T' N$temp = E; N$temp instanceof typeof($pattern) && <desugared $pattern>)
            //note the pattern desugaring performs binding variable assignments
            Type tempType = tree.expr.type.hasTag(BOT) ?
                    syms.objectType
                    : tree.expr.type;
            VarSymbol prevCurrentValue = currentValue;
            bindingContext = new BasicBindingContext();
            try {
                JCExpression translatedExpr = translate(tree.expr);
                Symbol exprSym = TreeInfo.symbol(translatedExpr);

                if (exprSym != null &&
                    exprSym.kind == Kind.VAR &&
                    exprSym.owner.kind.matches(Kinds.KindSelector.VAL_MTH)) {
                    currentValue = (VarSymbol) exprSym;
                } else {
                    currentValue = new VarSymbol(Flags.FINAL | Flags.SYNTHETIC,
                            names.fromString("patt" + tree.pos + target.syntheticNameChar() + "temp"),
                            tempType,
                            currentMethodSym);
                }

                Type principalType = principalType((JCPattern) tree.pattern);
                JCExpression resultExpression=
                        makeBinary(Tag.AND,
                                   makeTypeTest(make.Ident(currentValue), make.Type(principalType)),
                                   (JCExpression) this.<JCTree>translate(tree.pattern));
                if (currentValue != exprSym) {
                    resultExpression =
                            make.at(tree.pos).LetExpr(make.VarDef(currentValue, translatedExpr),
                                                      resultExpression).setType(syms.booleanType);
                    ((LetExpr) resultExpression).needsCond = true;
                }
                result = bindingContext.decorateExpression(resultExpression);
            } finally {
                currentValue = prevCurrentValue;
                bindingContext.pop();
            }
        } else {
            super.visitTypeTest(tree);
        }
    }

    @Override
    public void visitBindingPattern(JCBindingPattern tree) {
        //it is assumed the primary type has already been checked:
        BindingSymbol binding = (BindingSymbol) tree.var.sym;
        Type castTargetType = principalType(tree);
        VarSymbol bindingVar = bindingContext.bindingDeclared(binding);

        if (bindingVar != null) {
            JCAssign fakeInit = (JCAssign)make.at(TreeInfo.getStartPos(tree)).Assign(
                    make.Ident(bindingVar), convert(make.Ident(currentValue), castTargetType)).setType(bindingVar.erasure(types));
            LetExpr nestedLE = make.LetExpr(List.of(make.Exec(fakeInit)),
                                            make.Literal(true));
            nestedLE.needsCond = true;
            nestedLE.setType(syms.booleanType);
            result = nestedLE;
        } else {
            result = make.Literal(true);
        }
    }

    @Override
    public void visitParenthesizedPattern(JCParenthesizedPattern tree) {
        result = translate(tree.pattern);
    }

    @Override
    public void visitGuardPattern(JCGuardPattern tree) {
        JCExpression pattern = (JCExpression) this.<JCTree>translate(tree.patt);
        JCExpression guard = translate(tree.expr);
        result = makeBinary(Tag.AND, pattern, guard);
    }

//    public void visitDeconstructionPattern(JCDeconstructionPattern tree) {
//        //type test already done, finish handling of deconstruction patterns ("T(PATT1, PATT2, ...)")
//        //=>
//        //<PATT1-handling> && <PATT2-handling> && ...
//        List<? extends RecordComponent> components = tree.record.getRecordComponents();
//        List<? extends JCPattern> nestedPatterns = tree.nested;
//        JCExpression test = null;
//        while (components.nonEmpty() && nestedPatterns.nonEmpty()) {
//            //PATTn for record component COMPn of type Tn;
//            //PATTn is a type test pattern or a deconstruction pattern:
//            //=>
//            //(let Tn $c$COMPn = ((T) N$temp).COMPn(); <PATTn extractor>)
//            //or
//            //(let Tn $c$COMPn = ((T) N$temp).COMPn(); $c$COMPn != null && <PATTn extractor>)
//            //or
//            //(let Tn $c$COMPn = ((T) N$temp).COMPn(); $c$COMPn instanceof T' && <PATTn extractor>)
//            RecordComponent component = components.head;
//            JCPattern nested = nestedPatterns.head;
//            VarSymbol nestedTemp = new VarSymbol(Flags.SYNTHETIC,
//                names.fromString(target.syntheticNameChar() + "c" + target.syntheticNameChar() + component.name),
//                                 component.erasure(types),
//                                 currentMethodSym);
//            Symbol accessor = tree.record
//                                   .members()
//                                   .findFirst(component.name, s -> s.kind == Kind.MTH &&
//                                                                   ((MethodSymbol) s).params.isEmpty());
//            JCVariableDecl nestedTempVar =
//                    make.VarDef(nestedTemp,
//                                make.App(make.Select(convert(make.Ident(currentValue), tree.type),
//                                                     accessor)));
//            JCExpression extracted;
//            VarSymbol prevCurrentValue = currentValue;
//            try {
//                currentValue = nestedTemp;
//                extracted = (JCExpression) this.<JCTree>translate(nested);
//            } finally {
//                currentValue = prevCurrentValue;
//            }
//            JCExpression extraTest = null;
//            if (!types.isAssignable(nestedTemp.type, nested.type)) {
//                extraTest = makeTypeTest(make.Ident(nestedTemp),
//                                         make.Type(nested.type));
//            } else if (nested.type.isReference()) {
//                extraTest = makeBinary(Tag.NE, make.Ident(nestedTemp), makeNull());
//            }
//            if (extraTest != null) {
//                extracted = makeBinary(Tag.AND, extraTest, extracted);
//            }
//            LetExpr getAndRun = make.LetExpr(nestedTempVar, extracted);
//            getAndRun.needsCond = true;
//            getAndRun.setType(syms.booleanType);
//            if (test == null) {
//                test = getAndRun;
//            } else {
//                test = makeBinary(Tag.AND, test, getAndRun);
//            }
//            components = components.tail;
//            nestedPatterns = nestedPatterns.tail;
//        }
//        Assert.check(components.isEmpty() == nestedPatterns.isEmpty());
//        result = test != null ? test : makeLit(syms.booleanType, 1);
//    }
//
//    public void visitArrayPattern(JCArrayPattern tree) {
//        Type elementType = types.elemtype(tree.type);
//        List<? extends JCPattern> nestedPatterns 
//                = tree.nested;
//        JCExpression test = makeBinary(tree.orMore ? Tag.GE : Tag.EQ,
//                                make.Select(convert(make.Ident(currentValue), tree.type), syms.lengthVar),
//                                make.Literal(nestedPatterns.size()));
//
//        int i = 0;
//        while (nestedPatterns.nonEmpty()) {
//            //PATTn for record component COMPn of type Tn;
//            //PATTn is a type test pattern or a deconstruction pattern:
//            //=>
//            //(let Tn $c$COMPn = ((T) N$temp).COMPn(); <PATTn extractor>)
//            //or
//            //(let Tn $c$COMPn = ((T) N$temp).COMPn(); $c$COMPn != null && <PATTn extractor>)
//            //or
//            //(let Tn $c$COMPn = ((T) N$temp).COMPn(); $c$COMPn instanceof T' && <PATTn extractor>)
//            JCPattern nested = nestedPatterns.head;
//            VarSymbol nestedTemp = new VarSymbol(Flags.SYNTHETIC,
//                names.fromString(target.syntheticNameChar() + "c" + target.syntheticNameChar() + i),
//                                 types.erasure(elementType),
//                                 currentMethodSym);
//            JCVariableDecl nestedTempVar =
//                    make.VarDef(nestedTemp,
//                                make.Indexed(convert(make.Ident(currentValue), tree.type), make.Literal(i)).setType(elementType));
//            JCExpression extracted;
//            VarSymbol prevCurrentValue = currentValue;
//            try {
//                currentValue = nestedTemp;
//                extracted = (JCExpression) this.<JCTree>translate(nested);
//            } finally {
//                currentValue = prevCurrentValue;
//            }
//            JCExpression extraTest = null;
//            if (!types.isAssignable(nestedTemp.type, nested.type)) {
//                extraTest = makeTypeTest(make.Ident(nestedTemp),
//                                         make.Type(nested.type));
//            } else if (nested.type.isReference()) {
//                extraTest = makeBinary(Tag.NE, make.Ident(nestedTemp), makeNull());
//            }
//            if (extraTest != null) {
//                extracted = makeBinary(Tag.AND, extraTest, extracted);
//            }
//            LetExpr getAndRun = make.LetExpr(nestedTempVar, extracted);
//            getAndRun.needsCond = true;
//            getAndRun.setType(syms.booleanType);
//            test = makeBinary(Tag.AND, test, getAndRun);
//            i++;
//            nestedPatterns = nestedPatterns.tail;
//        }
//        result = test;
//    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        handleSwitch(tree, tree.selector, tree.cases, tree.hasTotalPattern, tree.patternSwitch);
    }

    @Override
    public void visitSwitchExpression(JCSwitchExpression tree) {
        handleSwitch(tree, tree.selector, tree.cases, tree.hasTotalPattern, tree.patternSwitch);
    }

    private void handleSwitch(JCTree tree,
                              JCExpression selector,
                              List<JCCase> cases,
                              boolean hasTotalPattern,
                              boolean patternSwitch) {
        Type seltype = selector.type;

        if (patternSwitch) {
            Assert.check(preview.isEnabled());
            Assert.check(preview.usesPreview(env.toplevel.sourcefile));

            //rewrite pattern matching switches:
            //switch ($obj) {
            //     case $constant: $stats$
            //     case $pattern1: $stats$
            //     case $pattern2, null: $stats$
            //     case $pattern3: $stats$
            //}
            //=>
            //int $idx = 0;
            //$RESTART: switch (invokeDynamic typeSwitch($constant, typeof($pattern1), typeof($pattern2), typeof($pattern3))($obj, $idx)) {
            //     case 0:
            //         if (!(<desugared $pattern1>)) { $idx = 1; continue $RESTART; }
            //         $stats$
            //     case 1:
            //         if (!(<desugared $pattern1>)) { $idx = 2; continue $RESTART; }
            //         $stats$
            //     case 2, -1:
            //         if (!(<desugared $pattern1>)) { $idx = 3; continue $RESTART; }
            //         $stats$
            //     case 3:
            //         if (!(<desugared $pattern1>)) { $idx = 4; continue $RESTART; }
            //         $stats$
            //}
            //notes:
            //-pattern desugaring performs assignment to the binding variables
            //-the selector is evaluated only once and stored in a temporary variable
            //-typeSwitch bootstrap method can restart matching at specified index. The bootstrap will
            // categorize the input, and return the case index whose type or constant matches the input.
            // The bootstrap does not evaluate guards, which are injected at the beginning of the case's
            // statement list, and if the guard fails, the switch is "continued" and matching is
            // restarted from the next index.
            //-case null is always desugared to case -1, as the typeSwitch bootstrap method will
            // return -1 when the input is null
            //
            //note the selector is evaluated only once and stored in a temporary variable
            ListBuffer<JCCase> newCases = new ListBuffer<>();
            for (List<JCCase> c = cases; c.nonEmpty(); c = c.tail) {
                if (c.head.stats.isEmpty() && c.tail.nonEmpty()) {
                    c.tail.head.labels = c.tail.head.labels.prependList(c.head.labels);
                } else {
                    newCases.add(c.head);
                }
            }
            cases = newCases.toList();
            ListBuffer<JCStatement> statements = new ListBuffer<>();
            VarSymbol temp = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString("selector" + tree.pos + target.syntheticNameChar() + "temp"),
                    seltype,
                    currentMethodSym);
            boolean hasNullCase = cases.stream()
                                       .flatMap(c -> c.labels.stream())
                                       .anyMatch(p -> p.isExpression() &&
                                                      TreeInfo.isNull((JCExpression) p));

            JCCase lastCase = cases.last();

            if (hasTotalPattern && !hasNullCase) {
                JCCase last = lastCase;
                if (last.labels.stream().noneMatch(l -> l.hasTag(Tag.DEFAULTCASELABEL))) {
                    last.labels = last.labels.prepend(makeLit(syms.botType, null));
                    hasNullCase = true;
                }
            }
            selector = translate(selector);
            statements.append(make.at(tree.pos).VarDef(temp, !hasNullCase ? attr.makeNullCheck(selector)
                                                                          : selector));
            VarSymbol index = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString(tree.pos + target.syntheticNameChar() + "index"),
                    syms.intType,
                    currentMethodSym);
            statements.append(make.at(tree.pos).VarDef(index, makeLit(syms.intType, 0)));

            List<Type> staticArgTypes = List.of(syms.methodHandleLookupType,
                                                syms.stringType,
                                                syms.methodTypeType,
                                                types.makeArrayType(syms.patternHandleType));
            Set<Symbol> capturedVariables = new LinkedHashSet<>();
                    cases.stream()
                         .flatMap(c -> c.labels.stream())
                         .filter(l -> l.isPattern())
                         .map(l -> (JCPattern) l)
                        .forEach(p ->
            new TreeScanner() {
                private final Set<Symbol> declared = new HashSet<>();
                @Override
                public void visitVarDef(JCVariableDecl tree) {
                    declared.add(tree.sym);
                    super.visitVarDef(tree);
                }

                @Override
                public void visitIdent(JCIdent tree) {
                    Symbol sym = tree.sym;
                    if (sym.kind == VAR || sym.kind == MTH) {
                        if (!declared.contains(sym)) {
                            VarSymbol v = (VarSymbol)sym;
                            if (v.getConstValue() == null) {
                                capturedVariables.add(v);
                            }
                        }
                    }
                }
            }.scan(p));

            List<JCCase> prevCaseList = null;

            for (List<JCCase> currentCaseList = cases; currentCaseList.nonEmpty(); currentCaseList = currentCaseList.tail) {
                JCCase currentCase = currentCaseList.head;
                boolean nullable = false;

                if (currentCase.labels.size() == 1 && currentCase.labels.head.isNullPattern() && currentCase.stats.isEmpty()) {
                    if (currentCaseList.tail.nonEmpty() && currentCaseList.tail.head.caseKind == CaseKind.STATEMENT && currentCaseList.tail.head.labels.size() == 1 && currentCaseList.tail.head.labels.head.isPattern()) {
                        nullable = true;
                        currentCaseList = currentCaseList.tail;
                        currentCase = currentCaseList.head;
                        if (currentCaseList == cases) {
                            cases = currentCaseList;
                        } else {
                            prevCaseList.tail = currentCaseList;
                        }
                    }
                }
                boolean isPattern = currentCase.labels.stream().anyMatch(l -> l.isPattern());
                boolean hasJoinedNull =
                        currentCase.labels.size() > 1 && currentCase.labels.stream().anyMatch(l -> l.isNullPattern());
                if (hasJoinedNull && isPattern) {
                    currentCase.labels = currentCase.labels.stream()
                                              .filter(l -> !l.isNullPattern())
                                              .collect(List.collector());
                    nullable = true;
                }
                if (nullable && /*XXX: should not otherwise compile - see CaseStructureTest???*/currentCase.labels.head.hasTag(Tag.BINDINGPATTERN)) {
                    ((JCBindingPattern) currentCase.labels.head).nullable = nullable;
                }
                prevCaseList = currentCaseList;
            }

            Map<JCPattern, List<BindingSymbol>> pattern2Bindings = new LinkedHashMap<>();
            LoadableConstant[] staticArgValues =
                    cases.stream()
                         .flatMap(c -> c.labels.stream())
                         .map(l -> toLoadableConstant(l, seltype, capturedVariables, pattern2Bindings))
                         .filter(c -> c != null)
                         .toArray(s -> new LoadableConstant[s]);

            Name bootstrapName = names.typeSwitch;
            Symbol bsm = rs.resolveInternalMethod(tree.pos(), env, syms.switchBootstrapsType,
                    bootstrapName, staticArgTypes, List.nil());

            MethodType indyType = new MethodType(
                    List.of(syms.objectType, types.makeArrayType(syms.objectType)),
                    syms.switchBootstrapsSwitchResultType,
                    List.nil(),
                    syms.methodClass
            );
            DynamicMethodSymbol dynSym = new DynamicMethodSymbol(bootstrapName,
                    syms.noSymbol,
                    ((MethodSymbol)bsm).asHandle(),
                    indyType,
                    staticArgValues);

            VarSymbol switchResult = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString("switchResult" + tree.pos + target.syntheticNameChar() + "temp"),
                    syms.switchBootstrapsSwitchResultType,
                    currentMethodSym);

            JCFieldAccess switchResultInitSelector = make.Select(make.QualIdent(bsm.owner), dynSym.name);
            switchResultInitSelector.sym = dynSym;
            switchResultInitSelector.type = syms.switchBootstrapsSwitchResultType;
            ListBuffer<JCExpression> dynamicArguments = new ListBuffer<>();
            dynamicArguments.add(make.Ident(temp));
            dynamicArguments.add(make.NewArray(make.Type(syms.objectType), List.nil(), capturedVariables.stream().map(v -> make.Ident(v)).collect(List.collector())).setType(types.makeArrayType(syms.objectType)));
            JCExpression switchResultInit = make.Apply(List.nil(),
                                  switchResultInitSelector,
                                  dynamicArguments.toList())
                           .setType(syms.switchBootstrapsSwitchResultType);
            statements.add(make.at(tree.pos).VarDef(switchResult, switchResultInit));

            MethodSymbol caseIndex = (MethodSymbol) syms.switchBootstrapsSwitchResultType.tsym.members().findFirst(names.fromString("caseIndex"));
            JCTree.JCMethodInvocation getCaseIndex = make.App(make.Select(make.Ident(switchResult), caseIndex), List.nil());
            getCaseIndex.type = syms.intType;

            selector = getCaseIndex;

            MethodSymbol carrier = (MethodSymbol) syms.switchBootstrapsSwitchResultType.tsym.members().findFirst(names.fromString("carrier"));
            MethodSymbol pattern = (MethodSymbol) syms.switchBootstrapsSwitchResultType.tsym.members().findFirst(names.fromString("handle"));
            MethodSymbol component = (MethodSymbol) syms.patternHandleType.tsym.members().findFirst(names.fromString("component"));
            MethodSymbol invoke = (MethodSymbol) syms.methodHandleType.tsym.members().findFirst(names.fromString("invoke"));

            int i = 0;
            boolean previousCompletesNormally = false;
            boolean hasDefault = false;

            for (var c : cases) {
                List<JCCaseLabel> clearedPatterns = c.labels;
                if (clearedPatterns.size() == 1 && clearedPatterns.head.isPattern() && !previousCompletesNormally) {
                    JCCaseLabel p = clearedPatterns.head;
                    bindingContext = new BindingDeclarationFenceBindingContext() {
                        @Override
                        VarSymbol getBindingFor(BindingSymbol varSymbol) {
                            return null;//???
                        }
                        
                    };
                    bindingContext = new BasicBindingContext();
                    VarSymbol prevCurrentValue = currentValue;
                    try {
                        currentValue = temp;
                        //XXX:
//                        JCExpression test = (JCExpression) this.<JCTree>translate(p);
                        List<BindingSymbol> bindings = pattern2Bindings.get(p);
                        for (BindingSymbol binding : bindings) {
                            if (binding != sentinel && bindingContext.getBindingFor(binding) == null) {
                                bindingContext.bindingDeclared(binding);
                            }
                        }

                        c.stats = translate(c.stats);
                        JCContinue continueSwitch = make.at(clearedPatterns.head.pos()).Continue(null);
                        continueSwitch.target = tree;
                        int componentIndex = 0;
                        for (BindingSymbol binding : bindings) {
                            if (binding != sentinel) {
                            JCTree.JCMethodInvocation getCarrier = make.App(make.Select(make.Ident(switchResult), carrier), List.nil()); //TODO: cache to variable?
                            getCarrier.type = syms.objectType;
                            JCTree.JCMethodInvocation getPattern = make.App(make.Select(make.Ident(switchResult), pattern), List.nil()); //TODO: cache to variable?
                            getPattern.type = syms.patternHandleType;
                            JCTree.JCMethodInvocation getComponent = make.App(make.Select(getPattern, component), List.of(makeLit(syms.intType, componentIndex)));
                            getComponent.type = syms.methodHandleType;
                            MethodSymbol tailoredInvoke = invoke.clone(invoke.owner);
                            tailoredInvoke.type = new MethodType(List.of(syms.objectType), syms.objectType, List.nil(), syms.methodClass);
                            JCExpression bindingExpr = make.TypeCast(binding.type, make.App(make.Select(getComponent, tailoredInvoke), List.of(getCarrier))).setType(syms.objectType); //TODO: should avoid the cast???
                            
                            Map<BindingSymbol, JCVariableDecl> bindingVars = bindingContext.bindingVars(c.pos);
                            c.stats = c.stats.prepend(make.Assignment(bindingVars.get(binding).sym, bindingExpr));
                            c.stats = c.stats.prepend(bindingVars.get(binding));
                            }
                            componentIndex++;
                        }
                    } finally {
                        currentValue = prevCurrentValue;
                        bindingContext.pop().pop();
                    }
                } else {
                    c.stats = translate(c.stats);
                }
                ListBuffer<JCCaseLabel> translatedLabels = new ListBuffer<>();
                for (var p : c.labels) {
                    if (p.hasTag(Tag.DEFAULTCASELABEL)) {
                        translatedLabels.add(p);
                        hasDefault = true;
                    } else if (hasTotalPattern && !hasDefault &&
                               c == lastCase && p.isPattern()) {
                        //If the switch has total pattern, the last case will contain it.
                        //Convert the total pattern to default:
                        translatedLabels.add(make.DefaultCaseLabel());
                    } else {
                        int value;
                        if (p.isNullPattern()) {
                            value = -1;
                        } else {
                            value = i++;
                        }
                        translatedLabels.add(make.Literal(value));
                    }
                }
                c.labels = translatedLabels.toList();
                if (c.caseKind == CaseTree.CaseKind.STATEMENT) {
                    previousCompletesNormally = c.completesNormally;
                } else {
                    previousCompletesNormally = false;
                    JCBreak brk = make.at(TreeInfo.endPos(c.stats.last())).Break(null);
                    brk.target = tree;
                    c.stats = c.stats.append(brk);
                }
            }

            if (tree.hasTag(Tag.SWITCH)) {
                ((JCSwitch) tree).selector = selector;
                ((JCSwitch) tree).cases = cases;
                statements.append((JCSwitch) tree);
                result = make.Block(0, statements.toList());
            } else {
                ((JCSwitchExpression) tree).selector = selector;
                ((JCSwitchExpression) tree).cases = cases;
                LetExpr r = (LetExpr) make.LetExpr(statements.toList(), (JCSwitchExpression) tree)
                                          .setType(tree.type);

                r.needsCond = true;
                result = r;
            }
            return ;
        }
        if (tree.hasTag(Tag.SWITCH)) {
            super.visitSwitch((JCSwitch) tree);
        } else {
            super.visitSwitchExpression((JCSwitchExpression) tree);
        }
    }

    private Type principalType(JCPattern p) {
        return types.boxedTypeOrType(types.erasure(TreeInfo.primaryPatternType(p).type()));
    }

    private final BindingSymbol sentinel = new BindingSymbol(0, null, Type.noType, null);
    private LoadableConstant toLoadableConstant(JCCaseLabel l, Type selector, Set<Symbol> capturedVariables, Map<JCPattern, List<BindingSymbol>> pattern2Bindings) {
        if (l.isPattern()) {
            class PatternConvertor extends JCTree.Visitor {
                DynamicVarSymbol description;
                ListBuffer<BindingSymbol> bindings = new ListBuffer<>();
                @Override
                public void visitBindingPattern(JCBindingPattern tree) {
                    Type patternType = types.boxedTypeOrType(types.erasure(tree.type));
                    List<Type> bsm_staticArgs = List.of(new ClassType(syms.classType.getEnclosingType(),
                                                                      List.of(patternType),
                                                                      syms.classType.tsym));
                    bsm_staticArgs = bsm_staticArgs.append(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(selector),
                                                                         syms.classType.tsym));
                    MethodSymbol ofType = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                            tree.nullable ? names.fromString("ofTypeNullable") : names.fromString("ofType"), bsm_staticArgs, List.nil());
                    MethodSymbol withCapturedVariables = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                            names.fromString("withCapturedVariables"), List.of(syms.patternHandleType, types.makeArrayType(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(syms.objectType),
                                                                         syms.classType.tsym))), List.nil());
                    ListBuffer<LoadableConstant> withCapturedVariablesArgs = new ListBuffer<>();
                    withCapturedVariablesArgs.add(makeCondyable(l.pos(), ofType, new LoadableConstant[] {(LoadableConstant) patternType, (ClassType) selector}));
                    capturedVariables.stream().map(s -> s.type).map(t -> /*XXX: should not be necessary!!!*/types.boxedTypeOrType(t)).map(t -> (ClassType) t).forEach(withCapturedVariablesArgs::add);
                    //TODO: bindings!
                    description = makeCondyable(l.pos(), withCapturedVariables, withCapturedVariablesArgs.toArray(s -> new LoadableConstant[s]));
                    bindings.add((BindingSymbol) tree.var.sym);
                    bindingContext.bindingDeclared((BindingSymbol) tree.var.sym);
                }

                @Override
                public void visitParenthesizedPattern(JCParenthesizedPattern tree) {
                    tree.pattern.accept(this);
                }

                @Override
                public void visitGuardPattern(JCGuardPattern tree) {
                    tree.patt.accept(this);

                    DynamicVarSymbol mainPatternDescription = description;

                    ListBuffer<Symbol> guardMethodParameters = new ListBuffer<>();
                    capturedVariables.stream().forEach(guardMethodParameters::add);
                    bindings.stream().forEach(guardMethodParameters::add);
                    MethodType guardMethodType = new MethodType(guardMethodParameters.stream().map(s -> s.type).collect(List.collector()), types.makeArrayType(syms.objectType), List.nil(), syms.methodClass);
                    MethodSymbol guardMethod = new MethodSymbol(/*XXX: capture this!*/Flags.STATIC | Flags.SYNTHETIC, names.fromString("$guard$" + tree.expr.getPreferredPosition()), guardMethodType, currentClass);

                    Iterator<Symbol> guardMethodParametersIt = guardMethodParameters.iterator();
                    Iterator<VarSymbol> parametersIt = guardMethod.params().iterator();
                    Map<Symbol, Symbol> originalVar2Parameter = new LinkedHashMap<>();
                    
                    while (guardMethodParametersIt.hasNext()) {
                        originalVar2Parameter.put(guardMethodParametersIt.next(), parametersIt.next());
                    }

                    bindingContext = new BindingDeclarationFenceBindingContext();
                    bindingContext = new BasicBindingContext();
                    int mark = bindings.size();

                    currentClass.members().enter(guardMethod);

                    //gather bindings from guards:
                    new TreeScanner() {
                        @Override
                        public void visitBindingPattern(JCBindingPattern tree) {
                            bindings.add((BindingSymbol) tree.var.sym);
                            bindingContext.bindingDeclared((BindingSymbol) tree.var.sym);
                            super.visitBindingPattern(tree);
                        }
                    }.scan(tree.expr);

                    JCExpression guardExpression = translate(new TreeTranslator() {
                        @Override
                        public void visitIdent(JCIdent tree) {
                            Symbol newSymbol = originalVar2Parameter.get(tree.sym);
                            if (newSymbol != null) {
                                result = make.Ident(newSymbol);
                            } else {
                                result = tree;
                            }
                        }
                    }.translate(tree.expr));

                    java.util.List<BindingSymbol> guardOutwardBindings = bindings.toList().subList(mark, bindings.size());

                    JCNewArray resultBindings = make.NewArray(make.Ident(syms.objectType.tsym), List.nil(), guardOutwardBindings.stream().map(var -> {
                        return bindingContext.getBindingFor(var);
                            }).map(var -> make.Ident(var)).collect(List.collector()));
                    resultBindings.type = types.makeArrayType(syms.objectType);
                    JCIf body = make.If(guardExpression, make.Return(resultBindings), make.Return(makeLit(syms.botType, null)));
                    ListBuffer<JCStatement> guardMethodBody = new ListBuffer<>();
                    guardMethodBody.addAll(bindingContext.bindingVars(l.pos).values());
                    guardMethodBody.add(body);
                    condyableMethods = condyableMethods.prepend(
                            make.MethodDef(guardMethod,
                                           guardMethod.externalType(types),
                                           make.Block(0, guardMethodBody.toList())));

                    MethodSymbol withGuard = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                            names.fromString("guarded"), List.of(syms.patternHandleType, syms.methodHandleType, types.makeArrayType(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(syms.objectType),
                                                                         syms.classType.tsym))), List.nil());
                    ListBuffer<LoadableConstant> withGuardArgs = new ListBuffer<>();
                    withGuardArgs.add((LoadableConstant) mainPatternDescription);
                    withGuardArgs.add(guardMethod.asHandle());
                    //TODO: primitive types, when allowed?
                    guardOutwardBindings.stream().map(s -> s.type).map(t -> (ClassType) t).forEach(withGuardArgs::add);
                    description = makeCondyable(l.pos(), withGuard, withGuardArgs.toArray(s -> new LoadableConstant[s]));
                    bindingContext.pop().pop();
                }

                public void visitDeconstructionPattern(JCDeconstructionPattern tree) {
                    Type patternType = types.boxedTypeOrType(types.erasure(tree.deconstructor.type));
                    List<Type> bsm_staticArgs = List.of(new ClassType(syms.classType.getEnclosingType(),
                                                                      List.of(patternType),
                                                                      syms.classType.tsym));
                    bsm_staticArgs = bsm_staticArgs.append(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(selector),
                                                                         syms.classType.tsym));
                    MethodSymbol ofType = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                            names.fromString("ofType"), bsm_staticArgs, List.nil());
                    MethodSymbol withCapturedVariables = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                            names.fromString("withCapturedVariables"), List.of(syms.patternHandleType, types.makeArrayType(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(syms.objectType),
                                                                         syms.classType.tsym))), List.nil());
                    ListBuffer<LoadableConstant> withCapturedVariablesArgs = new ListBuffer<>();
                    withCapturedVariablesArgs.add(makeCondyable(l.pos(), ofType, new LoadableConstant[] {(LoadableConstant) patternType, (ClassType) selector}));
                    capturedVariables.stream().map(s -> s.type).map(t -> /*XXX: should not be necessary!!!*/types.boxedTypeOrType(t)).map(t -> (ClassType) t).forEach(withCapturedVariablesArgs::add);
                    //TODO: bindings!
                    ListBuffer<LoadableConstant> withGuardArgs = new ListBuffer<>();
                    Symbol.DynamicVarSymbol outterPattern = makeCondyable(l.pos(), withCapturedVariables, withCapturedVariablesArgs.toArray(s -> new LoadableConstant[s]));
                    withGuardArgs.add(outterPattern);
                    bindings.add(sentinel);
                    
//                    ListBuffer<Symbol.DynamicVarSymbol> nested = new ListBuffer<>();
                    
                    for (JCPattern nestedPattern : tree.nested) {
                        nestedPattern.accept(this);
                        withGuardArgs.add(description);
                    }


                    MethodSymbol withGuard = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                            names.fromString("nested"), List.of(syms.patternHandleType, types.makeArrayType(syms.patternHandleType)), List.nil());
                    //TODO: primitive types, when allowed?
                    description = makeCondyable(l.pos(), withGuard, withGuardArgs.toArray(s -> new LoadableConstant[s]));
                }
            }
            PatternConvertor convertor = new PatternConvertor();
            l.accept(convertor);

            pattern2Bindings.put((JCPattern) l, convertor.bindings.toList());
            return convertor.description;
        } else if (l.isExpression() && !TreeInfo.isNull((JCExpression) l)) {
            if ((l.type.tsym.flags_field & Flags.ENUM) != 0) {
                List<Type> bsm_staticArgs = List.of(new ClassType(syms.classType.getEnclosingType(),
                                                              List.of(l.type),
                                                              syms.classType.tsym), syms.stringType);
                    bsm_staticArgs = bsm_staticArgs.append(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(selector),
                                                                         syms.classType.tsym));

                MethodSymbol ofEnumConstant = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                        names.fromString("ofEnumConstant"), bsm_staticArgs, List.nil());
                return makeCondyable(l.pos(), ofEnumConstant, new LoadableConstant[] {(LoadableConstant) l.type, LoadableConstant.String(((JCIdent) l).name.toString()), (ClassType) selector});
            } else {
                LoadableConstant constantEntry = switch (l.type.getTag()) {
                    case BYTE, CHAR,
                        SHORT, INT -> LoadableConstant.Int((Integer) l.type.constValue());
                    case CLASS -> LoadableConstant.String((String) l.type.constValue());
                    default -> throw new AssertionError();
                };
                    List<Type> bsm_staticArgs = List.of(syms.objectType);
                    bsm_staticArgs = bsm_staticArgs.append(new ClassType(syms.classType.getEnclosingType(),
                                                                         List.of(selector),
                                                                         syms.classType.tsym));

                MethodSymbol ofConstant = rs.resolveInternalMethod(l.pos(), env, syms.patternHandlesType,
                        names.fromString("ofConstant"), bsm_staticArgs, List.nil());
                return makeCondyable(l.pos(), ofConstant, new LoadableConstant[] {constantEntry, (ClassType) selector});

            }
        } else {
            return null;
        }
    }

    private Symbol.DynamicVarSymbol makeCondyable(DiagnosticPosition pos, MethodSymbol targetMethod, LoadableConstant[] parameters) {
//        Assert.checkNonNull(currentClass);
//
//        List<Type> bsm_staticArgs = List.of(syms.methodHandleLookupType,
//                                            syms.stringType,
//                                            new ClassType(syms.classType.getEnclosingType(),
//                                                          List.of(syms.patternHandlesType),
//                                                          syms.classType.tsym));
//        bsm_staticArgs = bsm_staticArgs.appendList(targetMethod.type.getParameterTypes());
//
//        MethodType indyType = new MethodType(bsm_staticArgs, targetMethod.type.getReturnType(), List.nil(), syms.methodClass);
//
//        MethodSymbol condyable = new MethodSymbol(Flags.STATIC | Flags.SYNTHETIC, names.fromString("$condyable$" + pos.getPreferredPosition()), indyType, currentClass);
//
//        if ((targetMethod.flags() & Flags.VARARGS) != 0) {
//            condyable.flags_field |= Flags.VARARGS;
//        }
//
//        currentClass.members().enter(condyable);
//
//        condyableMethods = condyableMethods.prepend(
//                make.MethodDef(condyable,
//                               condyable.externalType(types),
//                               make.Block(0, List.of(make.Return(make.Apply(List.nil(), make.QualIdent(targetMethod), condyable.params().stream().skip(3).map(p -> make.Ident(p)).collect(List.collector())).setType(syms.patternHandleType))))));
//
//        return new Symbol.DynamicVarSymbol(condyable.name,
//                                           syms.noSymbol,
//                                           new MethodHandleSymbol(condyable),
//                                           targetMethod.type.getReturnType(),
//                                           parameters);
        return new Symbol.DynamicVarSymbol(targetMethod.name,
                                           syms.noSymbol,
                                           new MethodHandleSymbol(targetMethod),
                                           targetMethod.type.getReturnType(),
                                           parameters);
    }

    @Override
    public void visitBinary(JCBinary tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitBinary(tree);
            result = bindingContext.decorateExpression(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitConditional(JCConditional tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitConditional(tree);
            result = bindingContext.decorateExpression(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitIf(JCIf tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitIf(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitForLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitWhileLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        bindingContext = new BasicBindingContext();
        try {
            super.visitDoLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            currentMethodSym = tree.sym;
            super.visitMethodDef(tree);
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    @Override
    public void visitIdent(JCIdent tree) {
        VarSymbol bindingVar = null;
        if ((tree.sym.flags() & Flags.MATCH_BINDING) != 0) {
            bindingVar = bindingContext.getBindingFor((BindingSymbol)tree.sym);
        }
        if (bindingVar == null) {
            super.visitIdent(tree);
        } else {
            result = make.at(tree.pos).Ident(bindingVar);
        }
    }

    @Override
    public void visitBlock(JCBlock tree) {
        ListBuffer<JCStatement> statements = new ListBuffer<>();
        bindingContext = new BindingDeclarationFenceBindingContext() {
            boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
                //{
                //    if (E instanceof T N) {
                //        return ;
                //    }
                //    //use of N:
                //}
                //=>
                //{
                //    T N;
                //    if ((let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp))) {
                //        return ;
                //    }
                //    //use of N:
                //}
                hoistedVarMap.put(binding, var.sym);
                statements.append(var);
                return true;
            }
        };
        MethodSymbol oldMethodSym = currentMethodSym;
        try {
            if (currentMethodSym == null) {
                // Block is a static or instance initializer.
                currentMethodSym =
                    new MethodSymbol(tree.flags | Flags.BLOCK,
                                     names.empty, null,
                                     currentClass);
            }
            for (List<JCStatement> l = tree.stats; l.nonEmpty(); l = l.tail) {
                statements.append(translate(l.head));
            }

            tree.stats = statements.toList();
            result = tree;
        } finally {
            currentMethodSym = oldMethodSym;
            bindingContext.pop();
        }
    }

    @Override
    public void visitLambda(JCLambda tree) {
        BindingContext prevContent = bindingContext;
        try {
            bindingContext = new BindingDeclarationFenceBindingContext();
            super.visitLambda(tree);
        } finally {
            bindingContext = prevContent;
        }
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        ClassSymbol prevCurrentClass = currentClass;
        List<JCTree> prevCondyableMethods = condyableMethods;
        try {
            currentClass = tree.sym;
            condyableMethods = List.nil();
            super.visitClassDef(tree);
        } finally {
            tree.defs = tree.defs.prependList(condyableMethods);
            currentClass = prevCurrentClass;
            condyableMethods = prevCondyableMethods;
            currentClass = prevCurrentClass;
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            tree.mods = translate(tree.mods);
            tree.vartype = translate(tree.vartype);
            if (currentMethodSym == null) {
                // A class or instance field initializer.
                currentMethodSym =
                    new MethodSymbol((tree.mods.flags&Flags.STATIC) | Flags.BLOCK,
                                     names.empty, null,
                                     currentClass);
            }
            if (tree.init != null) tree.init = translate(tree.init);
            result = tree;
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            this.make = make;
            this.env = env;
            translate(cdef);
        } finally {
            // note that recursive invocations of this method fail hard
            this.make = null;
            this.env = null;
        }

        return cdef;
    }

    /** Make an instanceof expression.
     *  @param lhs      The expression.
     *  @param type     The type to be tested.
     */

    JCInstanceOf makeTypeTest(JCExpression lhs, JCExpression type) {
        JCInstanceOf tree = make.TypeTest(lhs, type);
        tree.type = syms.booleanType;
        return tree;
    }

    /** Make an attributed binary expression (copied from Lower).
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCBinary makeBinary(JCTree.Tag optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = operators.resolveBinary(tree, optag, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    /** Make an attributed unary expression.
     *  @param optag    The operators tree tag.
     *  @param arg      The operator's argument.
     */
    JCTree.JCUnary makeUnary(JCTree.Tag optag, JCExpression arg) {
        JCTree.JCUnary tree = make.Unary(optag, arg);
        tree.operator = operators.resolveUnary(tree, optag, arg.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    JCExpression convert(JCExpression expr, Type target) {
        JCExpression result = make.at(expr.pos()).TypeCast(make.Type(target), expr);
        result.type = target;
        return result;
    }

    abstract class BindingContext {
        abstract VarSymbol bindingDeclared(BindingSymbol varSymbol);
        abstract VarSymbol getBindingFor(BindingSymbol varSymbol);
        abstract Map<BindingSymbol, JCVariableDecl> bindingVars(int diagPos);
        abstract JCStatement decorateStatement(JCStatement stat);
        abstract JCExpression decorateExpression(JCExpression expr);
        abstract BindingContext pop();
        abstract boolean tryPrepend(BindingSymbol binding, JCVariableDecl var);
    }

    class BasicBindingContext extends BindingContext {
        Map<BindingSymbol, VarSymbol> hoistedVarMap;
        BindingContext parent;

        public BasicBindingContext() {
            this.parent = bindingContext;
            this.hoistedVarMap = new LinkedHashMap<>();
        }

        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            VarSymbol res = parent.bindingDeclared(varSymbol);
            if (res == null) {
                res = new VarSymbol(varSymbol.flags(), varSymbol.name, varSymbol.type, currentMethodSym);
                res.setTypeAttributes(varSymbol.getRawTypeAttributes());
                hoistedVarMap.put(varSymbol, res);
            }
            return res;
        }

        @Override
        VarSymbol getBindingFor(BindingSymbol varSymbol) {
            VarSymbol res = parent.getBindingFor(varSymbol);
            if (res != null) {
                return res;
            }
            return hoistedVarMap.entrySet().stream()
                    .filter(e -> e.getKey().isAliasFor(varSymbol))
                    .findFirst()
                    .map(e -> e.getValue()).orElse(null);
        }

        @Override
        Map<BindingSymbol, JCVariableDecl> bindingVars(int diagPos) {
            if (hoistedVarMap.isEmpty()) return Collections.emptyMap();
            Map<BindingSymbol, JCVariableDecl> stats = new LinkedHashMap<>();
            for (Entry<BindingSymbol, VarSymbol> e : hoistedVarMap.entrySet()) {
                JCVariableDecl decl = makeHoistedVarDecl(diagPos, e.getValue());
                if (!e.getKey().isPreserved() ||
                    !parent.tryPrepend(e.getKey(), decl)) {
                    stats.put(e.getKey(), decl);
                }
            }
            return stats;
        }

        @Override
        JCStatement decorateStatement(JCStatement stat) {
            //if (E instanceof T N) {
            //     //use N
            //}
            //=>
            //{
            //    T N;
            //    if ((let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp))) {
            //        //use N
            //    }
            //}
            Map<BindingSymbol, JCVariableDecl> stats = bindingVars(stat.pos);
            if (!stats.isEmpty()) {
                stat = make.at(stat.pos).Block(0, List.<JCStatement>from(stats.values()).append(stat));
            }
            return stat;
        }

        @Override
        JCExpression decorateExpression(JCExpression expr) {
            //E instanceof T N && /*use of N*/
            //=>
            //(let T N; (let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp)) && /*use of N*/)
            for (VarSymbol vsym : hoistedVarMap.values()) {
                expr = make.at(expr.pos).LetExpr(makeHoistedVarDecl(expr.pos, vsym), expr).setType(expr.type);
            }
            return expr;
        }

        @Override
        BindingContext pop() {
            return bindingContext = parent;
        }

        @Override
        boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
            return false;
        }

        private JCVariableDecl makeHoistedVarDecl(int pos, VarSymbol varSymbol) {
            return make.at(pos).VarDef(varSymbol, null);
        }
    }

    private class BindingDeclarationFenceBindingContext extends BasicBindingContext {

        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            return null;
        }

    }

    /** Make an attributed tree representing a literal. This will be an
     *  Ident node in the case of boolean literals, a Literal node in all
     *  other cases.
     *  @param type       The literal's type.
     *  @param value      The literal's value.
     */
    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value).setType(type.constType(value));
    }

    /** Make an attributed tree representing null.
     */
    JCExpression makeNull() {
        return makeLit(syms.botType, null);
    }
}
