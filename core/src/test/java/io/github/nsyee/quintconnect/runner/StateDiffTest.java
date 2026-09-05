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
package io.github.nsyee.quintconnect.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nsyee.quintconnect.itf.ItfValue;
import org.junit.jupiter.api.Test;

class StateDiffTest {

  private static ItfValue.Record variant(String tag, ItfValue value) {
    return ItfValue.Record.of("tag", new ItfValue.Str(tag), "value", value);
  }

  @Test
  void prettyScalarsAndEmptyCollections() {
    assertEquals("1", StateDiff.pretty(ItfValue.Int.of(1)));
    assertEquals("\"a\"", StateDiff.pretty(new ItfValue.Str("a")));
    assertEquals("true", StateDiff.pretty(ItfValue.Bool.TRUE));
    assertEquals("Set()", StateDiff.pretty(ItfValue.Set.of()));
    assertEquals("Map()", StateDiff.pretty(ItfValue.Map.of()));
    assertEquals("List()", StateDiff.pretty(ItfValue.List.of()));
    assertEquals("()", StateDiff.pretty(ItfValue.Tuple.of()));
    assertEquals("{}", StateDiff.pretty(ItfValue.Record.of()));
    assertEquals("None", StateDiff.pretty(variant("None", ItfValue.Tuple.of())));
  }

  @Test
  void prettyNested() {
    ItfValue value =
        ItfValue.Record.of(
            "nextTurn",
            variant("X", ItfValue.Tuple.of()),
            "board",
            ItfValue.Map.of(
                new ItfValue.Map.Entry(
                    ItfValue.Int.of(1),
                    ItfValue.Set.of(
                        variant("Occupied", variant("O", ItfValue.Tuple.of())),
                        ItfValue.Tuple.of(ItfValue.Int.of(1), ItfValue.Int.of(2))))),
            "some",
            variant("Some", ItfValue.List.of(ItfValue.Int.of(3))));
    assertEquals(
        """
        {
          nextTurn: X,
          board: Map(
            1 -> Set(
              Occupied(
                O,
              ),
              (
                1,
                2,
              ),
            ),
          ),
          some: Some(
            List(
              3,
            ),
          ),
        }""",
        StateDiff.pretty(value));
  }

  @Test
  void unifiedDiff() {
    StateDiff diff =
        StateDiff.of(
            ItfValue.Record.of("a", ItfValue.Int.of(1), "b", ItfValue.Int.of(2)),
            ItfValue.Record.of("a", ItfValue.Int.of(1), "b", ItfValue.Int.of(3)));
    assertEquals(
        """
        --- specification
        +++ implementation
        @@ -1,4 +1,4 @@
         {
           a: 1,
        -  b: 2,
        +  b: 3,
         }""",
        diff.unified());
    assertEquals(diff.unified(), diff.toString());
  }

  @Test
  void identicalStatesProduceEmptyDiff() {
    StateDiff diff = new StateDiff("x", "x");
    assertEquals("", diff.unified());
  }
}
