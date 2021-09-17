/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import sun.invoke.util.BytecodeName;
import sun.invoke.util.Wrapper;

import static java.lang.invoke.MethodHandleInfo.REF_invokeInterface;
import static java.lang.invoke.MethodHandleInfo.REF_invokeStatic;
import static java.lang.invoke.MethodHandleInfo.REF_invokeVirtual;
import static java.lang.invoke.MethodHandleInfo.REF_newInvokeSpecial;
import static java.util.Objects.requireNonNull;

/**
 * Factories and combinators for {@link PatternHandle}s.
 */
public final class PatternHandles {
    private static final MethodHandle[] EMPTY_MH_ARRAY = new MethodHandle[0];
    private static final Object NULL_SENTINEL = new Object();

    private PatternHandles() {
    }

    // Factories

    /**
     * Returns a {@linkplain PatternHandle} for a <em>type pattern</em>, which
     * matches all non-null instances of the match type, with a single binding
     * variable which is the target cast to the match type.  The target type of
     * the resulting pattern is the match type; if a broader target type is
     * desired, use {@link #ofType(Class, Class)} or adapt the resulting pattern
     * handle with {@link #adaptTarget(PatternHandle, Class)}.
     *
     * @param matchType the type to match against
     * @return a pattern handle for a type pattern
     */
    public static PatternHandle ofType(Class<?> matchType) {
        requireNonNull(matchType);
        MethodType descriptor = MethodType.methodType(matchType, matchType);
        MethodHandle component = MethodHandles.identity(matchType);
        MethodHandle tryMatch
                = matchType.isPrimitive()
                  ? MethodHandles.identity(matchType)
                  : MH_OF_TYPE_TRY_MATCH.bindTo(matchType).asType(descriptor);

        return new PatternHandleImpl(descriptor, tryMatch, List.of(component));
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>type pattern</em>, which
     * matches all non-null instances of the match type, with a single binding
     * variable which is the target cast to the match type.  The target type of
     * the resulting pattern is the {@code targetType}.
     *
     * @param matchType  the type to match against
     * @param targetType the desired target type for the resulting pattern
     *                   handle
     * @return a pattern handle for a type pattern
     * @throws IllegalArgumentException if the provided match type and target
     *                                  type are not compatible
     */
    public static PatternHandle ofType(Class<?> matchType, Class<?> targetType) {
        return adaptTarget(ofType(matchType), targetType);
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>nullable type
     * pattern</em>, which matches all instances of the match type, plus {@code
     * null}, with a single binding variable which is the target cast to the
     * match type.  The target type of the resulting pattern is the match type;
     * if a broader target type is desired, use {@link #ofType(Class, Class)} or
     * adapt the resulting pattern handle with {@link #adaptTarget(PatternHandle,
     * Class)}.
     *
     * @param matchType the type to match against
     * @return a pattern handle for a nullable type pattern
     */
    public static PatternHandle ofTypeNullable(Class<?> matchType) {
        requireNonNull(matchType);
        MethodType descriptor = MethodType.methodType(matchType, matchType);
        MethodHandle component = MH_OF_TYPE_NULLABLE_COMPONENT
                .asType(MethodType.methodType(matchType, Object.class));
        MethodHandle tryMatch
                = matchType.isPrimitive()
                  ? MethodHandles.identity(matchType)
                  : MH_OF_TYPE_NULLABLE_TRY_MATCH.bindTo(matchType)
                                                 .asType(MethodType.methodType(Object.class, matchType));

        return new PatternHandleImpl(descriptor, tryMatch, List.of(component));
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>nullable type
     * pattern</em>, which matches all instances of the match type, plus {@code
     * null}, with a single binding variable which is the target cast to the
     * match type.  The target type of the resulting pattern is the {@code
     * targetType}.
     *
     * @param matchType  the type to match against
     * @param targetType the desired target type for the resulting pattern
     *                   handle
     * @return a pattern handle for a nullable type pattern
     * @throws IllegalArgumentException if the provided match type and target
     *                                  type are not compatible
     */
    public static PatternHandle ofTypeNullable(Class<?> matchType, Class<?> targetType) {
        return adaptTarget(ofTypeNullable(matchType), targetType);
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>constant pattern</em>,
     * which matches all instances that are {@link Object#equals(Object)} to
     * the specified constant.  The resulting pattern has no binding variables.
     * If the constant is {@code null}, the target type of the pattern is
     * {@link Object}, otherwise it is the result of {@code Object::getClass}
     * on the constant.
     *
     * <p>TODO: restrict type of constant to String, boxes, and enums?
     *
     * @param o the constant
     * @return a pattern handle for a constant pattern
     */
    public static PatternHandle ofConstant(Object o) {
        Class<?> type = o == null ? Object.class : o.getClass();
        MethodHandle match = partialize(MethodHandles.dropArguments(MethodHandles.constant(Object.class, Boolean.TRUE), 0, type),
                                        MethodHandles.insertArguments(MH_OBJECTS_EQUAL, 0, o)
                                                     .asType(MethodType.methodType(boolean.class, type)));
        return new PatternHandleImpl(MethodType.methodType(type), match, List.of());
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>constant pattern</em>,
     * which matches all instances that are {@link Object#equals(Object)} to
     * the specified constant.  The resulting pattern has no binding variables.
     * The target type of the pattern is {@code targetType}.
     *
     * @param o the constant
     * @param targetType the target type for the pattern
     * @return a pattern handle for a constant pattern
     * @throws IllegalArgumentException if the type of the constant and the
     * target type are not compatible
     */
    public static PatternHandle ofConstant(Object o, Class<?> targetType) {
        return adaptTarget(ofConstant(o), targetType);
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>constant pattern</em>,
     * which matches all instances that are {@link Object#equals(Object)} to
     * the specified enum constant.  The resulting pattern has no binding variables.
     * The target type of the pattern is {@code targetType}.
     *
     * @param enumClass the enum Class
     * @param constantName the constant name
     * @param <E> the enum
     * @return a pattern handle for a constant pattern
     * @throws IllegalArgumentException if the type of the constant and the
     * target type are not compatible
     */
    public static <E extends Enum<E>> PatternHandle ofEnumConstant(Class<E> enumClass, String constantName) {
        PatternHandle constant;
        try {
            constant = ofConstant(Enum.valueOf(enumClass, constantName));
        } catch (IllegalArgumentException ex) {
            constant = new PatternHandleImpl(MethodType.methodType(enumClass), MethodHandles.dropArguments(MethodHandles.constant(Object.class, null), 0, enumClass), Collections.emptyList());
        }
        return constant;
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>constant pattern</em>,
     * which matches all instances that are {@link Object#equals(Object)} to
     * the specified enum constant.  The resulting pattern has no binding variables.
     * The target type of the pattern is {@code targetType}.
     *
     * @param enumClass the enum Class
     * @param constantName the constant name
     * @param targetType the target type for the pattern
     * @param <E> the enum
     * @return a pattern handle for a constant pattern
     * @throws IllegalArgumentException if the type of the constant and the
     * target type are not compatible
     */
    public static <E extends Enum<E>> PatternHandle ofEnumConstant(Class<E> enumClass, String constantName, Class<?> targetType) {
        return adaptTarget(ofEnumConstant(enumClass, constantName), targetType);
    }

    // Combinators

    /**
     * Adapts a {@linkplain PatternHandle} to a new target type.  If the
     * pattern is of primitive type, it may be adapted to a supertype of its
     * corresponding box type; if it is of reference type, it may be widened
     * or narrowed to another reference type.
     *
     * @param pattern the pattern
     * @param newTarget the new target type
     * @return the adapted pattern
     * @throws IllegalArgumentException if the new target type is not compatible
     * with the target type of the pattern
     */
    public static PatternHandle adaptTarget(PatternHandle pattern, Class<?> newTarget) {
        Class<?> oldTarget = pattern.descriptor().returnType();
        if (oldTarget == newTarget)
            return pattern;

        Class<?> oldWrapperType = oldTarget.isPrimitive() ? Wrapper.forPrimitiveType(oldTarget).wrapperType() : null;
        MethodType guardType = MethodType.methodType(boolean.class, newTarget);
        MethodHandle guard;
        if (oldWrapperType != null && newTarget.isAssignableFrom(oldWrapperType)) {
            // Primitive boxing (with optional widening)
            guard = MH_PRIMITIVE_ADAPT_HELPER.bindTo(oldWrapperType).asType(guardType);
        }
        else if (newTarget.isAssignableFrom(oldTarget) || oldTarget.isAssignableFrom(newTarget)) {
            // reference narrowing or widening
            guard = MH_REFERENCE_ADAPT_HELPER.bindTo(oldTarget).asType(guardType);
        }
        else {
            throw new IllegalArgumentException(String.format("New target type %s not compatible with old target type %s",
                                                             newTarget, oldTarget));
        }

        MethodType tryMatchType = pattern.tryMatch().type().changeParameterType(0, newTarget);
        return new PatternHandleImpl(pattern.descriptor().changeReturnType(newTarget),
                                     partialize(pattern.tryMatch().asType(tryMatchType),
                                                guard),
                                     pattern.components());
    }

    /**
     * Returns a {@linkplain PatternHandle} for a <em>nested</em> pattern.  A
     * nested pattern first matches the target to the outer pattern, and if
     * it matches successfully, then matches the resulting bindings to the inner
     * patterns.  The resulting pattern matches if the outer pattern matches
     * the target, and the bindings match the appropriate inner patterns.  The
     * target type of the nested pattern is the same as the target type of
     * the outer pattern.  The bindings are the bindings for the outer pattern,
     * followed by the concatenation of the bindings for the inner patterns.
     *
     * @param outer  The outer pattern
     * @param inners The inner patterns, which can be null if no nested pattern
     *               for the corresponding binding is desired
     * @return the nested pattern
     */
    public static PatternHandle nested(PatternHandle outer, PatternHandle... inners) {
        PatternHandle[] patternHandles = inners.clone();
        int outerCount = outer.descriptor().parameterCount();
        Class<?> outerCarrierType = outer.tryMatch().type().returnType();

        // Adapt inners to types of outer bindings
        for (int i = 0; i < patternHandles.length; i++) {
            PatternHandle patternHandle = patternHandles[i];
            if (patternHandle.descriptor().returnType() != outer.descriptor().parameterType(i))
                patternHandles[i] = adaptTarget(patternHandle, outer.descriptor().parameterType(i));
        }

        int[] innerPositions = IntStream.range(0, patternHandles.length)
                                        .filter(i -> patternHandles[i] != null)
                                        .toArray();
        MethodHandle[] innerComponents = Stream.of(patternHandles)
                                               .filter(Objects::nonNull)
                                               .map(PatternHandle::components)
                                               .flatMap(List::stream)
                                               .toArray(MethodHandle[]::new);
        MethodHandle[] innerTryMatches = Stream.of(patternHandles)
                                               .filter(Objects::nonNull)
                                               .map(PatternHandle::tryMatch)
                                               .toArray(MethodHandle[]::new);
        Class<?>[] innerCarriers = Stream.of(patternHandles)
                                         .filter(Objects::nonNull)
                                         .map(e -> e.tryMatch().type().returnType())
                                         .toArray(Class[]::new);
        Class<?>[] innerTypes = Stream.of(innerComponents)
                                      .map(mh -> mh.type().returnType())
                                      .toArray(Class[]::new);

        MethodType descriptor = outer.descriptor().appendParameterTypes(innerTypes);

        MethodHandle mh = PatternCarriers.carrierFactory(descriptor);
        mh = MethodHandles.filterArguments(mh, outerCount, innerComponents);
        int[] spreadInnerCarriers = new int[outerCount + innerComponents.length];
        for (int i = 0; i < outerCount; i++)
            spreadInnerCarriers[i] = i;
        int k = outerCount;
        int j = 0;
        for (PatternHandle e : patternHandles) {
            if (e == null)
                continue;
            for (int i = 0; i < e.descriptor().parameterCount(); i++)
                spreadInnerCarriers[k++] = outerCount + j;
            j++;
        }
        MethodType spreadInnerCarriersMT = outer.descriptor()
                                                .appendParameterTypes(innerCarriers)
                                                .changeReturnType(mh.type().returnType());
        mh = MethodHandles.permuteArguments(mh, spreadInnerCarriersMT, spreadInnerCarriers);
        for (int position : innerPositions)
            mh = bailIfNthNull(mh, outerCount + position);
        mh = MethodHandles.filterArguments(mh, outerCount, innerTryMatches);
        int[] spreadNestedCarrier = new int[outerCount + innerPositions.length];
        for (int i = 0; i < outerCount; i++)
            spreadNestedCarrier[i] = i;
        for (int i = 0; i < innerPositions.length; i++)
            spreadNestedCarrier[outerCount + i] = innerPositions[i];
        mh = MethodHandles.permuteArguments(mh, outer.descriptor().changeReturnType(mh.type().returnType()),
                                            spreadNestedCarrier);
        mh = MethodHandles.filterArguments(mh, 0, outer.components().toArray(EMPTY_MH_ARRAY));
        mh = MethodHandles.permuteArguments(mh, MethodType.methodType(mh.type().returnType(), outerCarrierType),
                                            new int[outerCount]);
        mh = bailIfNthNull(mh, 0);
        mh = MethodHandles.filterArguments(mh, 0, outer.tryMatch());

        MethodHandle tryExtract = mh;

        return new PatternHandleImpl(descriptor, tryExtract, PatternCarriers.carrierComponents(descriptor));
    }

    /**
     * Construct a method handle that delegates to target, unless the nth
     * argument is null, in which case it returns null
     */
    private static MethodHandle bailIfNthNull(MethodHandle target, int n) {
        MethodHandle test = MH_OBJECTS_ISNULL
                .asType(MH_OBJECTS_ISNULL.type()
                                         .changeParameterType(0, target.type().parameterType(n)));
        test = MethodHandles.permuteArguments(test, target.type().changeReturnType(boolean.class), n);
        MethodHandle nullh = MethodHandles.dropArguments(MethodHandles.constant(target.type().returnType(), null),
                                                         0, target.type().parameterArray());
        return MethodHandles.guardWithTest(test, nullh, target);
    }


    /**
     * Augment the given pattern with a guard.
     * The resulting pattern has the same captured variables as the input pattern, and these are
     * also passed to the guard. The guard method must return a {@code Object[]} with the output
     * bindings.
     *
     * @param pattern input pattern
     * @param guard guard
     * @param outputTypes types of output bindings from the guard
     * @return the pattern with guard
     */
    public static PatternHandle guarded(PatternHandle pattern, MethodHandle guard, Class<?>... outputTypes) {
        MethodHandle tm = MH_TRY_MATCH_WITH_GUARD.bindTo(pattern).bindTo(guard).asCollector(Object[].class, pattern.tryMatch().type().parameterCount() - 1).asType(pattern.tryMatch().type().changeReturnType(Object[].class));
        List<MethodHandle> newComponents = new ArrayList<>();
        int idx = 0;
        for (MethodHandle c : pattern.components()) {
            newComponents.add(MethodHandles.filterArguments(c, 0, MethodHandles.insertArguments(MethodHandles.arrayElementGetter(Object[].class), 1, idx++)));
        }
        int i = 1;
        for (Class<?> output : outputTypes) {
            newComponents.add(MethodHandles.insertArguments(MethodHandles.arrayElementGetter(Object[].class), 1, i).asType(MethodType.methodType(output, Object[].class)));
        }
        List<Class<?>> newDescriptorTypes = new ArrayList<>();
        newDescriptorTypes.addAll(pattern.descriptor().parameterList());
        newDescriptorTypes.addAll(Arrays.asList(outputTypes));
        return new PatternHandleImpl(MethodType.methodType(pattern.descriptor().returnType(), newDescriptorTypes), tm, newComponents);
    }

    private static Object[] tryMatchWithGuard(PatternHandle pattern, MethodHandle guard, Object target, Object... captured) throws Throwable {
        MethodHandle tm = pattern.tryMatch().bindTo(target);
        for (Object c : captured) {
            tm = tm.bindTo(c);
        }
        Object carrier = tm.invoke();
        if (carrier != null) {
            int bindingCount = pattern.components().size();
            Object[] bindingArgs = new Object[bindingCount];
            for (int i = 0; i < bindingCount; i++) {
                bindingArgs[i] = pattern.component(i).invoke(carrier);
            }
            Object[] bindingsFromGuards = (Object[]) MethodHandles.insertArguments(MethodHandles.insertArguments(guard, 0, captured), 0, bindingArgs).invoke();
            if (bindingsFromGuards != null) {
                Object[] result = new Object[bindingsFromGuards.length + 1];
                result[0] = carrier;
                System.arraycopy(bindingsFromGuards, 0, result, 1, bindingsFromGuards.length);
                return result;
            }
        }
        return null;
    }

    /**
     * Augment the given pattern with captured variables.
     *
     * @param pattern the pattern to augment
     * @param captured captured variables to add
     * @return a pattern augmented with captured variables
     */
    public static PatternHandle withCapturedVariables(PatternHandle pattern, Class<?>... captured) {
        MethodHandle tm = MethodHandles.dropArguments(pattern.tryMatch(), 1, captured);
        return new PatternHandleImpl(pattern.descriptor(), tm, pattern.components());
    }

    // Helpers

    /**
     * Construct a partial method handle that uses the predicate as
     * guardWithTest, which applies the target if the test succeeds, and returns
     * null if the test fails.  The resulting method handle is of the same type
     * as the {@code target} method handle.
     *
     * @param target
     * @param predicate
     * @return
     */
    private static MethodHandle partialize(MethodHandle target,
                                           MethodHandle predicate) {
        Class<?> targetType = target.type().parameterType(0);
        Class<?> carrierType = target.type().returnType();
        return MethodHandles.guardWithTest(predicate,
                                           target,
                                           MethodHandles.dropArguments(MethodHandles.constant(carrierType, null),
                                                                       0, targetType));
    }

    private static MethodHandle lookupStatic(Class<?> clazz,
                                             String name,
                                             Class<?> returnType,
                                             Class<?>... paramTypes)
            throws ExceptionInInitializerError {
        try {
            return MethodHandles.lookup().findStatic(clazz, name, MethodType.methodType(returnType, paramTypes));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final MethodHandle MH_OF_TYPE_TRY_MATCH
            = lookupStatic(PatternHandles.class, "ofTypeTryMatch",
                           Object.class, Class.class, Object.class);
    private static final MethodHandle MH_OF_TYPE_NULLABLE_TRY_MATCH
            = lookupStatic(PatternHandles.class, "ofTypeNullableTryMatch",
                           Object.class, Class.class, Object.class);
    private static final MethodHandle MH_OF_TYPE_NULLABLE_COMPONENT
            = lookupStatic(PatternHandles.class, "ofTypeNullableComponent",
                           Object.class, Object.class);
    private static final MethodHandle MH_PRIMITIVE_ADAPT_HELPER
            = lookupStatic(PatternHandles.class, "primitiveAdaptHelper",
                           boolean.class, Class.class, Object.class);
    private static final MethodHandle MH_REFERENCE_ADAPT_HELPER
            = lookupStatic(PatternHandles.class, "referenceAdaptHelper",
                           boolean.class, Class.class, Object.class);
    private static final MethodHandle MH_OBJECTS_EQUAL
            = lookupStatic(Objects.class, "equals",
                           boolean.class, Object.class, Object.class);
    private static final MethodHandle MH_TRY_MATCH_WITH_GUARD
            = lookupStatic(PatternHandles.class, "tryMatchWithGuard",
                           Object[].class, PatternHandle.class, MethodHandle.class, Object.class, Object[].class);
    private static final MethodHandle MH_OBJECTS_ISNULL
            = lookupStatic(Objects.class, "isNull",
                           boolean.class, Object.class);

    private static Object ofTypeTryMatch(Class<?> type, Object o) {
        return o != null && type.isAssignableFrom(o.getClass())
               ? o
               : null;
    }

    private static Object ofTypeNullableTryMatch(Class<?> type, Object o) {
        if (o == null)
            return NULL_SENTINEL;
        else if (type.isAssignableFrom(o.getClass()))
            return o;
        else
            return null;
    }

    private static Object ofTypeNullableComponent(Object o) {
        return o == NULL_SENTINEL ? null : o;
    }

    private static boolean primitiveAdaptHelper(Class<?> type, Object o) {
        return o != null && type.isAssignableFrom(o.getClass());
    }

    private static boolean referenceAdaptHelper(Class<?> type, Object o) {
        return o == null || type.isAssignableFrom(o.getClass());
    }

    /**
     * Non-public implementation of {@link PatternHandle}
     */
    private static class PatternHandleImpl implements PatternHandle {

        private final MethodType descriptor;
        private final MethodHandle tryMatch;
        private final List<MethodHandle> components;


        /**
         * Construct an {@link PatternHandle} from components Constraints: -
         * output of tryMatch must match input of components - input of tryMatch
         * must match descriptor - output of components must match descriptor
         *
         * @param descriptor The {@code descriptor} method type
         * @param tryMatch   The {@code tryMatch} method handle
         * @param components The {@code component} method handles
         */
        PatternHandleImpl(MethodType descriptor, MethodHandle tryMatch,
                          List<MethodHandle> components) {
            MethodHandle[] componentsArray = components.toArray(new MethodHandle[0]);
            Class<?> carrierType = tryMatch.type().returnType();
            if (descriptor.parameterCount() != componentsArray.length)
                throw new IllegalArgumentException(String.format("MethodType %s arity should match component count %d",
                                                                 descriptor, componentsArray.length));
            if (!descriptor.returnType().equals(tryMatch.type().parameterType(0)))
                throw new IllegalArgumentException(String.format("Descriptor %s should match tryMatch input %s",
                                                                 descriptor, tryMatch.type()));
            for (int i = 0; i < componentsArray.length; i++) {
                MethodType componentType = componentsArray[i].type();
                if (componentType.parameterCount() != 1
                    || componentType.returnType().equals(void.class)
                    || !componentType.parameterType(0).equals(carrierType))
                    throw new IllegalArgumentException("Invalid component descriptor " + componentType);
                if (!componentType.returnType().equals(descriptor.parameterType(i)))
                    throw new IllegalArgumentException(String.format("Descriptor %s should match %d'th component %s",
                                                                     descriptor, i, componentsArray[i]));
            }

            if (!carrierType.equals(Object.class)) {
                tryMatch = tryMatch.asType(tryMatch.type().changeReturnType(Object.class));
                for (int i = 0; i < componentsArray.length; i++) {
                    MethodHandle component = componentsArray[i];
                    componentsArray[i] = component.asType(component.type().changeParameterType(0, Object.class));
                }
            }

            this.descriptor = descriptor;
            this.tryMatch = tryMatch;
            this.components = List.of(componentsArray);
        }

        @Override
        public MethodHandle tryMatch() {
            return tryMatch;
        }

        @Override
        public MethodHandle component(int i) {
            return components.get(i);
        }

        @Override
        public List<MethodHandle> components() {
            return components;
        }

        @Override
        public MethodType descriptor() {
            return descriptor;
        }

    }
}
