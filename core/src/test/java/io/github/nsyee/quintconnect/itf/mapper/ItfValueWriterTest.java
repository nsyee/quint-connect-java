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
package io.github.nsyee.quintconnect.itf.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reverse direction: Java values → {@link ItfValue}, in the canonical form used for diffs. */
class ItfValueWriterTest {

  private final ItfMapper mapper = new ItfMapper();

  enum Player {
    X,
    O
  }

  sealed interface Square permits Occupied, Empty {}

  record Occupied(Player player) implements Square {}

  record Empty() implements Square {}

  sealed interface Action permits Pair, Named, Prepares {}

  record Pair(int number, String text) implements Action {}

  @ItfVariant("renamed")
  record Named(long value) implements Action {}

  @ItfVariant(record = true)
  record Prepares(String node) implements Action {}

  record Position(@JsonProperty("row") int x, int y) {}

  record State(Optional<Position> last, List<Square> squares) {}

  static final class Bean {
    public int count = 3;
    public String name = "bean";
  }

  private static ItfValue itf(String json) {
    return ItfParser.parseValue(json);
  }

  @Test
  void scalars() {
    assertEquals(ItfValue.Bool.TRUE, mapper.toItf(true));
    assertEquals(ItfValue.Int.of(7), mapper.toItf(7));
    assertEquals(ItfValue.Int.of(7), mapper.toItf(7L));
    assertEquals(ItfValue.Int.of(7), mapper.toItf((short) 7));
    assertEquals(new ItfValue.Int(BigInteger.TEN), mapper.toItf(BigInteger.TEN));
    assertEquals(new ItfValue.Str("s"), mapper.toItf("s"));
    assertEquals(new ItfValue.Str("c"), mapper.toItf('c'));
  }

  @Test
  void itfValuesPassThrough() {
    ItfValue value = itf("{\"#set\": [1]}");
    assertEquals(value, mapper.toItf(value));
  }

  @Test
  void floatsAndNullAreRejected() {
    assertThrows(ItfMappingException.class, () -> mapper.toItf(1.5));
    assertThrows(ItfMappingException.class, () -> mapper.toItf(null));
  }

  @Test
  void enumIsTagOnlyVariant() {
    assertEquals(itf("{\"tag\": \"X\", \"value\": {\"#tup\": []}}"), mapper.toItf(Player.X));
  }

  @Test
  void optionalIsSomeOrNone() {
    assertEquals(itf("{\"tag\": \"Some\", \"value\": 1}"), mapper.toItf(Optional.of(1)));
    assertEquals(
        itf("{\"tag\": \"None\", \"value\": {\"#tup\": []}}"), mapper.toItf(Optional.empty()));
  }

  @Test
  void variantRecords() {
    assertEquals(itf("{\"tag\": \"Empty\", \"value\": {\"#tup\": []}}"), mapper.toItf(new Empty()));
    assertEquals(
        itf("{\"tag\": \"Occupied\", \"value\": {\"tag\": \"O\", \"value\": {\"#tup\": []}}}"),
        mapper.toItf(new Occupied(Player.O)));
    assertEquals(
        itf("{\"tag\": \"Pair\", \"value\": {\"number\": 1, \"text\": \"a\"}}"),
        mapper.toItf(new Pair(1, "a")));
    assertEquals(itf("{\"tag\": \"renamed\", \"value\": 5}"), mapper.toItf(new Named(5)));
    assertEquals(
        itf("{\"tag\": \"Prepares\", \"value\": {\"node\": \"p1\"}}"),
        mapper.toItf(new Prepares("p1")));
  }

  @Test
  void plainRecordsHonourJsonProperty() {
    assertEquals(itf("{\"row\": 1, \"y\": 2}"), mapper.toItf(new Position(1, 2)));
  }

  @Test
  void nestedRecord() {
    State state = new State(Optional.of(new Position(1, 2)), List.of(new Empty()));
    assertEquals(
        itf(
            "{\"last\": {\"tag\": \"Some\", \"value\": {\"row\": 1, \"y\": 2}},"
                + " \"squares\": [{\"tag\": \"Empty\", \"value\": {\"#tup\": []}}]}"),
        mapper.toItf(state));
  }

  @Test
  void listsAndArrays() {
    assertEquals(itf("[1, 2]"), mapper.toItf(List.of(1, 2)));
    assertEquals(itf("[1, 2]"), mapper.toItf(new int[] {1, 2}));
    assertEquals(itf("[\"a\"]"), mapper.toItf(new String[] {"a"}));
  }

  @Test
  void setsAreSortedForCanonicalDisplay() {
    Set<Integer> set = new LinkedHashSet<>(List.of(3, 1, 2));
    ItfValue.Set itf = (ItfValue.Set) mapper.toItf(set);
    assertEquals(List.of(ItfValue.Int.of(1), ItfValue.Int.of(2), ItfValue.Int.of(3)), itf.items());
  }

  @Test
  void mapsAreSortedByKeyAndKeepNonStringKeys() {
    Map<Integer, String> map = new LinkedHashMap<>();
    map.put(2, "b");
    map.put(1, "a");
    ItfValue.Map itf = (ItfValue.Map) mapper.toItf(map);
    assertEquals(
        List.of(
            new ItfValue.Map.Entry(ItfValue.Int.of(1), new ItfValue.Str("a")),
            new ItfValue.Map.Entry(ItfValue.Int.of(2), new ItfValue.Str("b"))),
        itf.entries());
    assertEquals(
        itf("{\"#map\": [[{\"row\": 1, \"y\": 1}, true]]}"),
        mapper.toItf(Map.of(new Position(1, 1), true)));
  }

  @Test
  void beansGoThroughJackson() {
    assertEquals(itf("{\"count\": 3, \"name\": \"bean\"}"), mapper.toItf(new Bean()));
  }

  @Test
  void roundTrip() {
    ItfValue original =
        itf(
            "{\"last\": {\"tag\": \"None\", \"value\": {\"#tup\": []}},"
                + " \"squares\": [{\"tag\": \"Occupied\", \"value\": {\"tag\": \"X\", \"value\": {\"#tup\": []}}}]}");
    State state = mapper.fromItf(original, State.class);
    assertEquals(original, mapper.toItf(state));
    assertTrue(mapper.toItf(state).display().contains("Occupied"));
  }
}
