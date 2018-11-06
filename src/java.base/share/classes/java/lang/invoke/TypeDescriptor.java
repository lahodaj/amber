/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke;

import java.util.List;

/**
 * An entity that has a field or method type descriptor, as per JVMS 4.3.2 or 4.3.3.
 * @jvms 4.3.2 Field Descriptors
 * @jvms 4.3.3 Method Descriptors
 *
 * @since 12
 */
public interface TypeDescriptor {
    /**
     * Return the type descriptor for this instance, which may be a field or method type descriptor.
     * @return the type descriptor
     */
    String descriptorString();


    /**
     * Represents a field type descriptor, as per JVMS 4.3.2.
     *
     * @param <F> the class implementing {@linkplain TypeDescriptor.OfField}
     * @jvms 4.3.2 Field Descriptors
     * @since 12
     */
    interface OfField<F extends TypeDescriptor.OfField<F>> extends TypeDescriptor {
        /**
         * Does this field descriptor describe an array type?
         * @return whether this field descriptor describes an array type
         */
        boolean isArray();

        /**
         * Does this field descriptor describe a primitive type?
         * @return whether this field descriptor describes a primitive type
         */
        boolean isPrimitive();

        /**
         * If this field descriptor describes an array type, return
         * a descriptor for its component type, otherwise return {@code null}.
         * @return the component type, or {@code null} if this field descriptor does
         * not describe an array type
         */
        F componentType();

        /**
         * Return a descriptor for the array type whose component type is described by this
         * descriptor
         * @return the descriptor for the array type
         */
        F arrayType();
    }


    /**
     * Represents a method type descriptor, as per JVMS 4.3.3
     *
     * @param <F> the type representing field type descriptors
     * @param <M> the class implementing {@linkplain TypeDescriptor.OfMethod}
     * @jvms 4.3.3 Method Descriptors
     * @since 12
     */
    interface OfMethod<F extends TypeDescriptor.OfField<F>, M extends TypeDescriptor.OfMethod<F, M>>
            extends TypeDescriptor {

        /**
         * Return the number of parameters in the method type
         * @return the number of parameters
         */
        int parameterCount();

        /**
         * Return a field descriptor describing the requested parameter of the method type
         * described by this descriptor
         * @param i the index of the parameter
         * @return a field descriptor for the requested parameter type
         * @throws IndexOutOfBoundsException if the index is outside the half-open
         * range {[0, parameterCount)}
         */
        F parameterType(int i);

        /**
         * Return a field descriptor describing the return type of the method type described
         * by this descriptor
         * @return a field descriptor for the return type
         */
        F returnType();

        /**
         * Return an array of field descriptors for the parameter types of the method type
         * described by this descriptor
         * @return field descriptors for the parameter types
         */
        F[] parameterArray();

        /**
         * Return a list of field descriptors for the parameter types of the method type
         * described by this descriptor
         * @return field descriptors for the parameter types
         */
        List<F> parameterList();

        /**
         * Return a method descriptor that is identical to this one, except that the return
         * type has been changed to the specified type
         *
         * @param newReturn a field descriptor for the new return type
         * @throws NullPointerException if any argument is {@code null}
         * @return the new method descriptor
         */
        M changeReturnType(F newReturn);

        /**
         * Return a method descriptor that is identical to this one,
         * except that a single parameter type has been changed to the specified type.
         *
         * @param index the index of the parameter to change
         * @param paramType a field descriptor describing the new parameter type
         * @return the new method descriptor
         * @throws NullPointerException if any argument is {@code null}
         * @throws IndexOutOfBoundsException if the index is outside the half-open
         * range {[0, parameterCount)}
         */
        M changeParameterType(int index, F paramType);

        /**
         * Return a method descriptor that is identical to this one,
         * except that a range of parameter types have been removed.
         *
         * @param start the index of the first parameter to remove
         * @param end the index after the last parameter to remove
         * @return the new method descriptor
         * @throws IndexOutOfBoundsException if {@code start} is outside the half-open
         * range {[0, parameterCount)}, or {@code end} is outside the closed range
         * {@code [0, parameterCount]}
         */
        M dropParameterTypes(int start, int end);

        /**
         * Return a method descriptor that is identical to this one,
         * except that a range of additional parameter types have been inserted.
         *
         * @param pos the index at which to insert the first inserted parameter
         * @param paramTypes field descriptors describing the new parameter types
         *                   to insert
         * @return the new method descriptor
         * @throws NullPointerException if any argument is {@code null}
         * @throws IndexOutOfBoundsException if {@code pos} is outside the closed
         * range {[0, parameterCount]}
         */
        @SuppressWarnings("unchecked")
        M insertParameterTypes(int pos, F... paramTypes);
    }
}
