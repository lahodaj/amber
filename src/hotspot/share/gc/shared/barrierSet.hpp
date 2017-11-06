/*
 * Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_VM_GC_SHARED_BARRIERSET_HPP
#define SHARE_VM_GC_SHARED_BARRIERSET_HPP

#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/fakeRttiSupport.hpp"

// This class provides the interface between a barrier implementation and
// the rest of the system.

class BarrierSet: public CHeapObj<mtGC> {
  friend class VMStructs;
public:
  // Fake RTTI support.  For a derived class T to participate
  // - T must have a corresponding Name entry.
  // - GetName<T> must be specialized to return the corresponding Name
  //   entry.
  // - If T is a base class, the constructor must have a FakeRtti
  //   parameter and pass it up to its base class, with the tag set
  //   augmented with the corresponding Name entry.
  // - If T is a concrete class, the constructor must create a
  //   FakeRtti object whose tag set includes the corresponding Name
  //   entry, and pass it up to its base class.

  enum Name {                   // associated class
    ModRef,                     // ModRefBarrierSet
    CardTableModRef,            // CardTableModRefBS
    CardTableForRS,             // CardTableModRefBSForCTRS
    CardTableExtension,         // CardTableExtension
    G1SATBCT,                   // G1SATBCardTableModRefBS
    G1SATBCTLogging             // G1SATBCardTableLoggingModRefBS
  };

protected:
  typedef FakeRttiSupport<BarrierSet, Name> FakeRtti;

private:
  FakeRtti _fake_rtti;

  // Metafunction mapping a class derived from BarrierSet to the
  // corresponding Name enum tag.
  template<typename T> struct GetName;

  // Downcast argument to a derived barrier set type.
  // The cast is checked in a debug build.
  // T must have a specialization for BarrierSet::GetName<T>.
  template<typename T> friend T* barrier_set_cast(BarrierSet* bs);

public:
  // Note: This is not presently the Name corresponding to the
  // concrete class of this object.
  BarrierSet::Name kind() const { return _fake_rtti.concrete_tag(); }

  // Test whether this object is of the type corresponding to bsn.
  bool is_a(BarrierSet::Name bsn) const { return _fake_rtti.has_tag(bsn); }

  // End of fake RTTI support.

protected:
  BarrierSet(const FakeRtti& fake_rtti) : _fake_rtti(fake_rtti) { }
  ~BarrierSet() { }

public:
  // Invoke the barrier, if any, necessary when writing "new_val" into the
  // ref field at "offset" in "obj".
  // (For efficiency reasons, this operation is specialized for certain
  // barrier types.  Semantically, it should be thought of as a call to the
  // virtual "_work" function below, which must implement the barrier.)
  // First the pre-write versions...
  template <class T> inline void write_ref_field_pre(T* field, oop new_val);

  // ...then the post-write version.
  inline void write_ref_field(void* field, oop new_val, bool release = false);

protected:
  virtual void write_ref_field_pre_work(      oop* field, oop new_val) {};
  virtual void write_ref_field_pre_work(narrowOop* field, oop new_val) {};
  virtual void write_ref_field_work(void* field, oop new_val, bool release) = 0;

public:
  // Operations on arrays, or general regions (e.g., for "clone") may be
  // optimized by some barriers.

  // Below length is the # array elements being written
  virtual void write_ref_array_pre(oop* dst, int length,
                                   bool dest_uninitialized = false) {}
  virtual void write_ref_array_pre(narrowOop* dst, int length,
                                   bool dest_uninitialized = false) {}
  // Below count is the # array elements being written, starting
  // at the address "start", which may not necessarily be HeapWord-aligned
  inline void write_ref_array(HeapWord* start, size_t count);

  // Static versions, suitable for calling from generated code;
  // count is # array elements being written, starting with "start",
  // which may not necessarily be HeapWord-aligned.
  static void static_write_ref_array_pre(HeapWord* start, size_t count);
  static void static_write_ref_array_post(HeapWord* start, size_t count);

  virtual void write_ref_nmethod_pre(oop* dst, nmethod* nm) {}
  virtual void write_ref_nmethod_post(oop* dst, nmethod* nm) {}

protected:
  virtual void write_ref_array_work(MemRegion mr) = 0;

public:
  // (For efficiency reasons, this operation is specialized for certain
  // barrier types.  Semantically, it should be thought of as a call to the
  // virtual "_work" function below, which must implement the barrier.)
  void write_region(MemRegion mr);

protected:
  virtual void write_region_work(MemRegion mr) = 0;

public:
  // Inform the BarrierSet that the the covered heap region that starts
  // with "base" has been changed to have the given size (possibly from 0,
  // for initialization.)
  virtual void resize_covered_region(MemRegion new_region) = 0;

  // If the barrier set imposes any alignment restrictions on boundaries
  // within the heap, this function tells whether they are met.
  virtual bool is_aligned(HeapWord* addr) = 0;

  // Print a description of the memory for the barrier set
  virtual void print_on(outputStream* st) const = 0;
};

template<typename T>
inline T* barrier_set_cast(BarrierSet* bs) {
  assert(bs->is_a(BarrierSet::GetName<T>::value), "wrong type of barrier set");
  return static_cast<T*>(bs);
}

#endif // SHARE_VM_GC_SHARED_BARRIERSET_HPP
