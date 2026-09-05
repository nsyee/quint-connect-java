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
package io.github.nsyee.quintconnect.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Port of the {@code driver/nondet.rs} unit tests plus display checks. */
class NondetPicksTest {

  private static Record some(int value) {
    return Record.of("tag", new Str("Some"), "value", Int.of(value));
  }

  private static final Record NONE = Record.of("tag", new Str("None"), "value", Tuple.of());

  @Test
  void failToBuildNondetPicks() {
    DriverException e = assertThrows(DriverException.class, () -> NondetPicks.of(Int.of(42)));
    assertEquals("Expected nondet picks to be a `Value::Record`", e.getMessage());
  }

  @Test
  void getNondetPick() {
    NondetPicks picks = NondetPicks.of(Record.of("foo", some(42)));
    assertEquals(Optional.of(Int.of(42)), picks.get("foo"));
    assertTrue(picks.contains("foo"));
  }

  @Test
  void noneIsDropped() {
    NondetPicks picks = NondetPicks.of(Record.of("foo", NONE, "bar", some(1)));
    assertFalse(picks.contains("foo"));
    assertEquals(Optional.empty(), picks.get("foo"));
    assertEquals(List.of("bar"), List.copyOf(picks.names()));
    assertEquals(1, picks.size());
  }

  @Test
  void nonOptionValuesPassThrough() {
    NondetPicks picks = NondetPicks.of(Record.of("foo", Int.of(7)));
    assertEquals(Optional.of(Int.of(7)), picks.get("foo"));
  }

  @Test
  void emptyPicks() {
    assertTrue(NondetPicks.empty().isEmpty());
    assertEquals(NondetPicks.empty(), NondetPicks.of(Record.of("foo", NONE)));
    assertEquals("", NondetPicks.empty().toString());
  }

  @Test
  void displayMatchesRust() {
    NondetPicks picks = NondetPicks.of(Record.of("a", some(1), "b", new Str("x")));
    assertEquals("+ a: 1\n+ b: \"x\"", picks.toString());
  }
}
