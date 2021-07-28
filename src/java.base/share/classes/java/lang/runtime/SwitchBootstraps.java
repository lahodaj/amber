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

package java.lang.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.stream.Stream;

import jdk.internal.javac.PreviewFeature;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap methods for linking {@code invokedynamic} call sites that implement
 * the selection functionality of the {@code switch} statement.  The bootstraps
 * take additional static arguments corresponding to the {@code case} labels
 * of the {@code switch}, implicitly numbered sequentially from {@code [0..N)}.
 *
 * @since 17
 */
@PreviewFeature(feature=PreviewFeature.Feature.SWITCH_PATTERN_MATCHING)
public class SwitchBootstraps {

    private SwitchBootstraps() {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle DO_TYPE_SWITCH;

    static {
        try {
            DO_TYPE_SWITCH = LOOKUP.findStatic(SwitchBootstraps.class, "doTypeSwitch",
                                           MethodType.methodType(SwitchResult.class, Object.class, PatternHandle[].class, Object[].class));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Bootstrap method for linking an {@code invokedynamic} call site that
     * implements a {@code switch} on a target of a reference type.  The static
     * arguments are an array of case labels which must be non-null and of type
     * {@code String} or {@code Integer} or {@code Class}.
     * <p>
     * The type of the returned {@code CallSite}'s method handle will have
     * a return type of {@code int}.   It has two parameters: the first argument
     * will be an {@code Object} instance ({@code target}) and the second
     * will be {@code int} ({@code restart}).
     * <p>
     * If the {@code target} is {@code null}, then the method of the call site
     * returns {@literal -1}.
     * <p>
     * If the {@code target} is not {@code null}, then the method of the call site
     * returns the index of the first element in the {@code labels} array starting from
     * the {@code restart} index matching one of the following conditions:
     * <ul>
     *   <li>the element is of type {@code Class} that is assignable
     *       from the target's class; or</li>
     *   <li>the element is of type {@code String} or {@code Integer} and
     *       equals to the target.</li>
     * </ul>
     * <p>
     * If no element in the {@code labels} array matches the target, then
     * the method of the call site return the length of the {@code labels} array.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller.  When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName unused
     * @param invocationType The invocation type of the {@code CallSite} with two parameters,
     *                       a reference type, an {@code int}, and {@code int} as a return type.
     * @param labels case labels - {@code String} and {@code Integer} constants
     *               and {@code Class} instances, in any combination
     * @return a {@code CallSite} returning the first matching element as described above
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any element in the labels array is null, if the
     * invocation type is not not a method type of first parameter of a reference type,
     * second parameter of type {@code int} and with {@code int} as its return type,
     * or if {@code labels} contains an element that is not of type {@code String},
     * {@code Integer} or {@code Class}.
     * @jvms 4.4.6 The CONSTANT_NameAndType_info Structure
     * @jvms 4.4.10 The CONSTANT_Dynamic_info and CONSTANT_InvokeDynamic_info Structures
     */
    public static CallSite typeSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      PatternHandle... labels) {
//        if (invocationType.parameterCount() != 2
//            || (!invocationType.returnType().equals(SwitchResult.class))
//            || invocationType.parameterType(0).isPrimitive()
//            || !invocationType.parameterType(1).equals(int.class))
//            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(labels);

        labels = labels.clone();
//        Stream.of(labels).forEach(SwitchBootstraps::verifyLabel);

        MethodHandle target = MethodHandles.insertArguments(DO_TYPE_SWITCH, 1, (Object) labels).asVarargsCollector(Object[].class);
        return new ConstantCallSite(target);
    }

//    private static void verifyLabel(Object label) {
//        if (label == null) {
//            throw new IllegalArgumentException("null label found");
//        }
//        Class<?> labelClass = label.getClass();
//        if (labelClass != Class.class &&
//            labelClass != String.class &&
//            labelClass != Integer.class) {
//            throw new IllegalArgumentException("label with illegal type found: " + label.getClass());
//        }
//    }

    private static SwitchResult doTypeSwitch(Object target, PatternHandle[] labels, Object...captured) throws Throwable { //TODO: document the exceptions!
        // Dumbest possible strategy
        for (int i =0; i < labels.length; i++) {
            PatternHandle label = labels[i];
            MethodHandle tm = label.tryMatch().bindTo(target);
            for (Object c : captured) {
                tm = tm.bindTo(c);
            }
            Object carrier = tm.invoke();
            if (carrier != null) {
                return new SwitchResult(i, label, carrier);
            }
        }

        if (target == null)
            return new SwitchResult(-1, null, null);

        return new SwitchResult(labels.length, null, null);
    }

    /**
     * T
     */
    public record SwitchResult(int caseIndex, PatternHandle handle, Object carrier) {}
}
