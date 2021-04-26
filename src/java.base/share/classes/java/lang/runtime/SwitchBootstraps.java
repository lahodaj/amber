/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.internal.javac.PreviewFeature;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap methods for linking {@code invokedynamic} call sites that implement
 * the selection functionality of the {@code switch} statement.  The bootstraps
 * take additional static arguments corresponding to the {@code case} labels
 * of the {@code switch}, implicitly numbered sequentially from {@code [0..N)}.
 *
 * <p>The bootstrap call site accepts a single parameter of the type of the
 * operand of the {@code switch}, and return an {@code int} that is the index of
 * the matched {@code case} label, {@code -1} if the target is {@code null},
 * or {@code N} if the target is not null but matches no {@code case} label.
 *
 * @since 17
 */
@PreviewFeature(feature=PreviewFeature.Feature.SWITCH_PATTERN_MATCHING)
public class SwitchBootstraps {

    private SwitchBootstraps() {}

    // Shared INIT_HOOK for all switch call sites; looks the target method up in a map
    private static final MethodHandle TYPE_INIT_HOOK;
    private static final MethodHandle TYPE_SWITCH_METHOD;


    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static {
        try {
            TYPE_INIT_HOOK = LOOKUP.findStatic(SwitchBootstraps.class, "typeInitHook",
                                                  MethodType.methodType(MethodHandle.class, CallSite.class));
            TYPE_SWITCH_METHOD = LOOKUP.findVirtual(TypeSwitchCallSite.class, "doSwitch",
                                                    MethodType.methodType(int.class, Object.class, int.class));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static<T extends CallSite> MethodHandle typeInitHook(T receiver) {
        return TYPE_SWITCH_METHOD.bindTo(receiver);
    }

    /**
     * Bootstrap method for linking an {@code invokedynamic} call site that
     * implements a {@code switch} on a reference-typed target.  The static
     * arguments are a varargs array of {@code Class} labels.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller.  When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName The invocation name, which is ignored.  When used with
     *                       {@code invokedynamic}, this is provided by the
     *                       {@code NameAndType} of the {@code InvokeDynamic}
     *                       structure and is stacked automatically by the VM.
     * @param invocationType The invocation type of the {@code CallSite}.  This
     *                       method type should have a single parameter of
     *                       a reference type, and return {@code int}.  When
     *                       used with {@code invokedynamic}, this is provided by
     *                       the {@code NameAndType} of the {@code InvokeDynamic}
     *                       structure and is stacked automatically by the VM.
     * @param types non-null {@link Class} values
     * @return the index into {@code labels} of the target value, if the target
     *         is an instance of any of the types, {@literal -1} if the target
     *         value is {@code null}, or {@code types.length} if the target value
     *         is not an instance of any of the types
     * @throws NullPointerException if any required argument is null
     * @throws IllegalArgumentException if any labels are null, or if the
     * invocation type is not {@code (T)int for some reference type {@code T}}
     * @throws Throwable if there is any error linking the call site
     */
    public static CallSite typeSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      Class<?>... types) throws Throwable {
        if (invocationType.parameterCount() != 2
            || (!invocationType.returnType().equals(int.class))
            || invocationType.parameterType(0).isPrimitive())
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(types);

        types = types.clone();
        if (Stream.of(types).anyMatch(Objects::isNull))
            throw new IllegalArgumentException("null label found");

        assert Stream.of(types).distinct().count() == types.length
                : "switch labels are not distinct: " + Arrays.toString(types);

        return new TypeSwitchCallSite(invocationType, types);
    }

    static class TypeSwitchCallSite extends ConstantCallSite {
        private final Class<?>[] types;

        TypeSwitchCallSite(MethodType targetType,
                           Class<?>[] types) throws Throwable {
            super(targetType, TYPE_INIT_HOOK);
            this.types = types;
        }

        int doSwitch(Object target, int startIndex) {
            if (target == null)
                return -1;

            // Dumbest possible strategy
            Class<?> targetClass = target.getClass();
            for (int i = startIndex; i < types.length; i++) {
                Class<?> c = types[i];
                if (c.isAssignableFrom(targetClass))
                    return i;
            }

            return types.length;
        }
    }

}
