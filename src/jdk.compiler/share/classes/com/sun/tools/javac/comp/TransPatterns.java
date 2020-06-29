/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.BindingSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
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
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import java.util.Map;
import java.util.Map.Entry;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.TypeTag;
import static com.sun.tools.javac.code.TypeTag.BOT;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCExpressionPattern;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCPattern;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import com.sun.tools.javac.tree.JCTree.LetExpr;
import com.sun.tools.javac.util.List;
import java.util.HashMap;

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
    private final Log log;
    private final ConstFold constFold;
    private final Names names;
    private final Target target;
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

    private MethodSymbol currentMethodSym = null;

    protected TransPatterns(Context context) {
        context.put(transPatternsKey, this);
        syms = Symtab.instance(context);
        attr = Attr.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        operators = Operators.instance(context);
        log = Log.instance(context);
        constFold = ConstFold.instance(context);
        names = Names.instance(context);
        target = Target.instance(context);
        debugTransPatterns = Options.instance(context).isSet("debug.patterns");
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        if (tree.pattern.hasTag(Tag.BINDINGPATTERN)) {
            //E instanceof T N
            //=>
            //(let T' N$temp = E; N$temp instanceof T && (N = (T) N$temp == (T) N$temp))
            JCBindingPattern patt = (JCBindingPattern)tree.pattern;
            VarSymbol pattSym = patt.symbol;
            Type tempType = tree.expr.type.hasTag(BOT) ?
                    syms.objectType
                    : tree.expr.type;
            VarSymbol temp = new VarSymbol(pattSym.flags() | Flags.SYNTHETIC,
                    names.fromString(pattSym.name.toString() + target.syntheticNameChar() + "temp"),
                    tempType,
                    patt.symbol.owner);
            JCExpression translatedExpr = translate(tree.expr);
            Type castTargetType = types.boxedTypeOrType(pattSym.erasure(types));

            result = makeTypeTest(make.Ident(temp), make.Type(castTargetType));

            VarSymbol bindingVar = bindingContext.bindingDeclared(patt.symbol);
            if (bindingVar != null) { //TODO: cannot be null here?
                JCAssign fakeInit = (JCAssign)make.at(tree.pos).Assign(
                        make.Ident(bindingVar), convert(make.Ident(temp), castTargetType)).setType(bindingVar.erasure(types));
                LetExpr nestedLE = make.LetExpr(List.of(make.Exec(fakeInit)),
                                                make.Literal(true));
                nestedLE.needsCond = true;
                nestedLE.setType(syms.booleanType);
                result = makeBinary(Tag.AND, (JCExpression)result, nestedLE);
            }
            result = make.at(tree.pos).LetExpr(make.VarDef(temp, translatedExpr), (JCExpression)result).setType(syms.booleanType);
            ((LetExpr) result).needsCond = true;
        } else {
            super.visitTypeTest(tree);
        }
    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        handleSwitch(tree, tree.selector, tree.cases);
    }

    @Override
    public void visitSwitchExpression(JCSwitchExpression tree) {
        handleSwitch(tree, tree.selector, tree.cases);
    }

    private void handleSwitch(JCTree tree,
                              JCExpression selector,
                              List<JCCase> cases) {
        Type seltype = selector.type;
        //from Attr.handleSwitch:
            boolean enumSwitch = (seltype.tsym.flags() & Flags.ENUM) != 0;
            boolean stringSwitch = types.isSameType(seltype, syms.stringType);
            boolean typeTestSwitch = !seltype.isPrimitive() && !types.unboxedType(seltype).hasTag(TypeTag.INT) && !enumSwitch && !stringSwitch;
        if (typeTestSwitch) {
            ListBuffer<JCStatement> statements = new ListBuffer<>();
            VarSymbol temp = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString(tree.pos + target.syntheticNameChar() + "temp"),
                    seltype,
                    currentMethodSym); //XXX:owner - verify field inits!
            statements.append(make.at(tree.pos).VarDef(temp, attr.makeNullCheck(selector)));
            VarSymbol index = new VarSymbol(Flags.SYNTHETIC,
                    names.fromString(tree.pos + target.syntheticNameChar() + "index"),
                    syms.intType,
                    currentMethodSym); //XXX:owner - verify field inits!
            statements.append(make.at(tree.pos).VarDef(index, makeLit(syms.intType, 0)));
            List<Type> staticArgTypes = List.of(syms.methodHandleLookupType,
                                                syms.stringType,
                                                syms.methodTypeType,
                                                types.makeArrayType(new ClassType(syms.classType.getEnclosingType(),
                                                                  List.of(new WildcardType(syms.objectType, BoundKind.UNBOUND,
                                                                                           syms.boundClass)),
                                                                  syms.classType.tsym)));
            LoadableConstant[] staticArgValues = cases.stream().flatMap(c -> c.pats.stream()).map(p -> (JCBindingPattern) p).map(p -> p.symbol.type).toArray(s -> new LoadableConstant[s]);

            Symbol bsm = rs.resolveInternalMethod(tree.pos(), env, syms.switchBootstrapsType,
                    names.fromString("typeSwitch"), staticArgTypes, List.nil());

            MethodType indyType = new MethodType(
                    List.of(syms.objectType, syms.intType),
                    syms.intType,
                    List.nil(),
                    syms.methodClass
            );
        DynamicMethodSymbol dynSym = new DynamicMethodSymbol(names.fromString("typeSwitch"),
                syms.noSymbol,
                ((MethodSymbol)bsm).asHandle(),
                indyType,
                staticArgValues);

        JCFieldAccess qualifier = make.Select(make.QualIdent(bsm.owner), dynSym.name);
        qualifier.sym = dynSym;
        qualifier.type = syms.intType;

        selector = make.Apply(List.nil(), qualifier, List.of(make.Ident(temp), make.Ident(index))).setType(syms.intType);

        int i = 0;
        boolean previousCompletesNormally = false;

        for (var c : cases) {
            if (c.pats.size() == 1 && !previousCompletesNormally) {
                JCBindingPattern b = (JCBindingPattern) c.pats.head;
                VarSymbol binding = (b).symbol;
                c.stats = translate(c.stats);
                c.stats = c.stats.prepend(make.VarDef(binding, make.TypeCast(binding.type, make.Ident(temp))));
                if (b.guard != null) {
                    VarSymbol tempBinding = new VarSymbol(Flags.SYNTHETIC,
                            names.fromString(tree.pos + target.syntheticNameChar() + binding.name.toString()),
                            binding.type,
                            currentMethodSym); //XXX:owner - verify field inits!
                    JCContinue continueSwitch = make.Continue(null);
                    continueSwitch.target = tree;
                    JCExpression updatedGuard = new TreeTranslator() {
                        @Override
                        public void visitIdent(JCIdent tree) {
                            super.visitIdent(tree);
                            if (tree.sym == b.symbol) {
                                result = make.TypeCast(b.symbol.type, make.Ident(tempBinding));
                            }
                        }
                    }.translate(b.guard);

                    c.stats = c.stats.prepend(make.Block(0, List.of(make.VarDef(tempBinding, make.TypeCast(binding.type, make.Ident(temp))), //duplicate, so that the variable is removed before the continue finishes - might be possible to optimize
                                                                    make.If(make.Unary(Tag.NOT, updatedGuard).setType(syms.booleanType),
                                                                            make.Block(0, List.of(make.Exec(make.Assign(make.Ident(index),
                                                                                                  makeLit(syms.intType, i + 1)).setType(syms.intType)), continueSwitch)),
                                                                            null))));
                }
            }
            ListBuffer<JCPattern> translatedLabels = new ListBuffer<>();
            for (var p : c.pats) {
                translatedLabels.add(make.ExpressionPattern(make.Literal(i++)));
            }
            c.pats = translatedLabels.toList();
            previousCompletesNormally = c.completesNormally;
        }
        
        if (tree.hasTag(Tag.SWITCH)) {
            ((JCSwitch) tree).selector = selector;
            statements.append((JCSwitch) tree);
            result = make.Block(0, statements.toList());
        } else {
            ((JCSwitchExpression) tree).selector = selector;
            LetExpr r = (LetExpr) make.LetExpr(statements.toList(), (JCSwitchExpression) tree).setType(tree.type);

            r.needsCond = true;
            result = r;
        }
        return ;
        //TODO: add null (-1) handling!
        }
        if (tree.hasTag(Tag.SWITCH)) {
            super.visitSwitch((JCSwitch) tree);
        } else {
            super.visitSwitchExpression((JCSwitchExpression) tree);
        }
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
        try {
            for (List<JCStatement> l = tree.stats; l.nonEmpty(); l = l.tail) {
                statements.append(translate(l.head));
            }

            tree.stats = statements.toList();
            result = tree;
        } finally {
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

    JCExpression convert(JCExpression expr, Type target) {
        JCExpression result = make.at(expr.pos()).TypeCast(make.Type(target), expr);
        result.type = target;
        return result;
    }

    abstract class BindingContext {
        abstract VarSymbol bindingDeclared(BindingSymbol varSymbol);
        abstract VarSymbol getBindingFor(BindingSymbol varSymbol);
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
            this.hoistedVarMap = new HashMap<>();
        }

        @Override
        VarSymbol bindingDeclared(BindingSymbol varSymbol) {
            VarSymbol res = parent.bindingDeclared(varSymbol);
            if (res == null) {
                res = new VarSymbol(varSymbol.flags(), varSymbol.name, varSymbol.type, varSymbol.owner);
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
        JCStatement decorateStatement(JCStatement stat) {
            if (hoistedVarMap.isEmpty()) return stat;
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
            ListBuffer<JCStatement> stats = new ListBuffer<>();
            for (Entry<BindingSymbol, VarSymbol> e : hoistedVarMap.entrySet()) {
                JCVariableDecl decl = makeHoistedVarDecl(stat.pos, e.getValue());
                if (!e.getKey().isPreserved() ||
                    !parent.tryPrepend(e.getKey(), decl)) {
                    stats.add(decl);
                }
            }
            if (stats.nonEmpty()) {
                stats.add(stat);
                stat = make.at(stat.pos).Block(0, stats.toList());
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
}
