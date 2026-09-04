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

import io.github.nsyee.quintconnect.itf.ItfValue.Bool;
import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.List;
import io.github.nsyee.quintconnect.itf.ItfValue.Map;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Set;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import io.github.nsyee.quintconnect.itf.ItfValue.Unserializable;
import org.junit.jupiter.api.Test;

/** Port of the {@code value/display.rs} unit tests. */
class ItfDisplayTest {

  @Test
  void scalars() {
    assertDisplay(Bool.TRUE, "true");
    assertDisplay(Int.of(42), "42");
    assertDisplay(new Str("foo"), "\"foo\"");
    assertDisplay(Int.of("123456789012345678901234567890"), "123456789012345678901234567890");
    assertDisplay(Int.of(-7), "-7");
  }

  @Test
  void list() {
    assertDisplay(List.of(Int.of(42), Bool.TRUE), "List(42, true)");
    assertDisplay(List.of(), "List()");
  }

  @Test
  void tuple() {
    assertDisplay(Tuple.of(Int.of(42), Bool.TRUE), "(42, true)");
    assertDisplay(Tuple.of(), "()");
  }

  @Test
  void set() {
    assertDisplay(Set.of(Bool.TRUE, Int.of(42)), "Set(true, 42)");
    assertDisplay(Set.of(), "Set()");
  }

  @Test
  void map() {
    assertDisplay(
        Map.of(
            new Map.Entry(new Str("bool"), Bool.FALSE), new Map.Entry(new Str("num"), Int.of(42))),
        "Map(\"bool\" -> false, \"num\" -> 42)");
    assertDisplay(Map.of(), "Map()");
  }

  @Test
  void record() {
    assertDisplay(Record.of("bool", Bool.FALSE, "num", Int.of(42)), "{ bool: false, num: 42 }");
    assertDisplay(Record.of(), "{  }");
  }

  @Test
  void taggedRecord() {
    assertDisplay(Record.of("tag", new Str("Foo"), "value", Tuple.of()), "Foo");
    assertDisplay(Record.of("tag", new Str("Foo"), "value", Tuple.of(Int.of(42))), "Foo(42)");
    assertDisplay(
        Record.of("tag", new Str("Foo"), "value", Tuple.of(Int.of(1), Int.of(2))), "Foo(1, 2)");
    assertDisplay(Record.of("tag", new Str("Some"), "value", Int.of(42)), "Some(42)");
    assertDisplay(
        Record.of(
            "tag",
            new Str("Occupied"),
            "value",
            Record.of("tag", new Str("X"), "value", Tuple.of())),
        "Occupied(X)");
    assertDisplay(
        Record.of("tag", new Str("Foo"), "value", Record.of("a", Int.of(1))), "Foo({ a: 1 })");
  }

  @Test
  void recordsThatOnlyLookLikeVariantsAreNotSpecialCased() {
    assertDisplay(Record.of("tag", Int.of(1), "value", Int.of(2)), "{ tag: 1, value: 2 }");
    assertDisplay(
        Record.of("tag", new Str("Foo"), "value", Int.of(2), "extra", Int.of(3)),
        "{ tag: \"Foo\", value: 2, extra: 3 }");
    assertDisplay(Record.of("tag", new Str("Foo")), "{ tag: \"Foo\" }");
  }

  @Test
  void unserializable() {
    assertDisplay(new Unserializable("Nat"), "\"Nat\"");
    assertDisplay(new Unserializable("say \"hi\"\\n"), "\"say \\\"hi\\\"\\\\n\"");
  }

  @Test
  void nested() {
    ItfValue board =
        Map.of(
            new Map.Entry(
                Int.of(1),
                Map.of(
                    new Map.Entry(
                        Int.of(1), Record.of("tag", new Str("Empty"), "value", Tuple.of())))));
    assertDisplay(board, "Map(1 -> Map(1 -> Empty))");
    assertEquals(board.display(), ItfDisplay.format(board));
  }

  private static void assertDisplay(ItfValue value, String expected) {
    assertEquals(expected, ItfDisplay.format(value));
  }
}
