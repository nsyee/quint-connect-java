/*
 * Copyright 2026 the quint-connect-java authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nsyee.quintconnect.itf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.List;
import io.github.nsyee.quintconnect.itf.ItfValue.Map;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Set;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItfValueTest {

  @Test
  void setEqualityIgnoresOrderAndDuplicates() {
    Set a = Set.of(Int.of(1), Int.of(2));
    Set b = Set.of(Int.of(2), Int.of(1), Int.of(2));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, Set.of(Int.of(1)));
    assertEquals(java.util.Set.of(Int.of(1), Int.of(2)), b.toSet());
  }

  @Test
  void mapEqualityIgnoresOrder() {
    Map a = Map.of(new Map.Entry(Int.of(1), new Str("a")), new Map.Entry(Int.of(2), new Str("b")));
    Map b = Map.of(new Map.Entry(Int.of(2), new Str("b")), new Map.Entry(Int.of(1), new Str("a")));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, Map.of(new Map.Entry(Int.of(1), new Str("a"))));
    assertEquals(Optional.of(new Str("b")), a.get(Int.of(2)));
    assertEquals(Optional.empty(), a.get(Int.of(3)));
  }

  @Test
  void nestedSetsInsideMapsCompareStructurally() {
    Map a = Map.of(new Map.Entry(new Str("k"), Set.of(Int.of(1), Int.of(2))));
    Map b = Map.of(new Map.Entry(new Str("k"), Set.of(Int.of(2), Int.of(1))));
    assertEquals(a, b);
    assertTrue(Set.of(a).equals(Set.of(b)));
  }

  @Test
  void listsAndTuplesAreOrdered() {
    assertNotEquals(List.of(Int.of(1), Int.of(2)), List.of(Int.of(2), Int.of(1)));
    assertNotEquals(Tuple.of(Int.of(1), Int.of(2)), Tuple.of(Int.of(2), Int.of(1)));
    assertNotEquals(List.of(Int.of(1)), Tuple.of(Int.of(1)));
  }

  @Test
  void recordEqualityIgnoresFieldOrderButKeepsItForIteration() {
    Record a = Record.of("x", Int.of(1), "y", Int.of(2));
    Record b = Record.of("y", Int.of(2), "x", Int.of(1));
    assertEquals(a, b);
    assertEquals(java.util.List.of("x", "y"), java.util.List.copyOf(a.fields().keySet()));
    assertEquals(java.util.List.of("y", "x"), java.util.List.copyOf(b.fields().keySet()));
  }

  @Test
  void recordHelpers() {
    Record rec = Record.of("tag", new Str("Foo"), "value", Int.of(1));
    assertEquals(Optional.of("Foo"), rec.variantTag());
    assertEquals(Optional.empty(), Record.of("tag", new Str("Foo")).variantTag());
    assertEquals(Optional.empty(), Record.of("a", Int.of(1)).variantTag());
    assertEquals(Record.of("value", Int.of(1)), rec.without("tag"));
    assertEquals(rec, rec.without("missing"));
    assertTrue(rec.has("tag"));
    assertEquals(2, rec.size());
    assertThrows(IllegalArgumentException.class, () -> Record.of("only-a-name"));
  }

  @Test
  void collectionsAreImmutable() {
    List list = List.of(Int.of(1));
    assertThrows(UnsupportedOperationException.class, () -> list.items().add(Int.of(2)));
    Record rec = Record.of("a", Int.of(1));
    assertThrows(UnsupportedOperationException.class, () -> rec.fields().put("b", Int.of(2)));
  }
}
