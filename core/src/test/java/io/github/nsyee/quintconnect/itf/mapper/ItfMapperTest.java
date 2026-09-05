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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** One test per mapping rule of {@link ItfModule}, plus negative cases. */
class ItfMapperTest {

  private final ItfMapper mapper = new ItfMapper();

  private static ItfValue itf(String json) {
    return ItfParser.parseValue(json);
  }

  // --- fixtures
  // -----------------------------------------------------------------------------------

  enum Player {
    X,
    O
  }

  sealed interface Square permits Occupied, Empty {}

  record Occupied(Player player) implements Square {}

  record Empty() implements Square {}

  record Position(int x, int y) {}

  record Pair(int number, String text) {}

  record Coord(int row, int col) {}

  sealed interface Action permits Init, Move, Pair2, Named, Inner {}

  record Init() implements Action {}

  record Move(String node) implements Action {}

  record Pair2(int number, String text) implements Action {}

  @ItfVariant("renamed")
  record Named(long value) implements Action {}

  sealed interface Inner extends Action permits Deep {}

  record Deep(Optional<Integer> maybe) implements Inner {}

  record Renamed(@JsonProperty("nextTurn") Player turn) {}

  record WithOptional(Optional<Integer> maybe, String name) {}

  record Ints(int i, long l, BigInteger big) {}

  // --- scalars
  // ------------------------------------------------------------------------------------

  @Nested
  class Scalars {
    @Test
    void booleansAndStrings() {
      assertEquals(true, mapper.fromItf(ItfValue.Bool.TRUE, boolean.class));
      assertEquals(false, mapper.fromItf(ItfValue.Bool.FALSE, Boolean.class));
      assertEquals("hi", mapper.fromItf(new ItfValue.Str("hi"), String.class));
    }

    @Test
    void integersOfEveryWidth() {
      assertEquals(42, mapper.fromItf(ItfValue.Int.of(42), int.class));
      assertEquals(Integer.valueOf(-1), mapper.fromItf(ItfValue.Int.of(-1), Integer.class));
      assertEquals(1L << 40, mapper.fromItf(ItfValue.Int.of(1L << 40), long.class));
      ItfValue huge = itf("{\"#bigint\": \"123456789012345678901234567890\"}");
      assertEquals(
          new BigInteger("123456789012345678901234567890"), mapper.fromItf(huge, BigInteger.class));
      Ints ints =
          mapper.fromItf(itf("{\"i\": 1, \"l\": 2, \"big\": {\"#bigint\": \"3\"}}"), Ints.class);
      assertEquals(new Ints(1, 2, BigInteger.valueOf(3)), ints);
    }

    @Test
    void intOverflowIsAnError() {
      ItfValue tooBig = ItfValue.Int.of(1L << 40);
      ItfMappingException e =
          assertThrows(ItfMappingException.class, () -> mapper.fromItf(tooBig, int.class));
      assertTrue(e.getMessage().contains("1099511627776"), e.getMessage());
    }

    @Test
    void longOverflowIsAnError() {
      ItfValue tooBig = itf("{\"#bigint\": \"123456789012345678901234567890\"}");
      assertThrows(ItfMappingException.class, () -> mapper.fromItf(tooBig, long.class));
      assertThrows(ItfMappingException.class, () -> mapper.fromItf(tooBig, Long.class));
    }

    @Test
    void wrongScalarTypeIsAnError() {
      assertThrows(
          ItfMappingException.class, () -> mapper.fromItf(new ItfValue.Str("x"), int.class));
      assertThrows(
          ItfMappingException.class, () -> mapper.fromItf(ItfValue.Int.of(1), boolean.class));
    }
  }

  // --- records
  // ------------------------------------------------------------------------------------

  @Nested
  class Records {
    @Test
    void recordFromItfRecord() {
      assertEquals(new Position(1, 2), mapper.fromItf(itf("{\"x\": 1, \"y\": 2}"), Position.class));
    }

    @Test
    void unknownFieldsAreIgnored() {
      assertEquals(
          new Position(1, 2),
          mapper.fromItf(itf("{\"x\": 1, \"y\": 2, \"z\": 3}"), Position.class));
    }

    @Test
    void missingFieldIsAnError() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class, () -> mapper.fromItf(itf("{\"x\": 1}"), Position.class));
      assertTrue(e.getMessage().contains("y"), e.getMessage());
    }

    @Test
    void jsonPropertyRenames() {
      assertEquals(
          new Renamed(Player.O),
          mapper.fromItf(
              itf("{\"nextTurn\": {\"tag\": \"O\", \"value\": {\"#tup\": []}}}"), Renamed.class));
    }

    @Test
    void tupleToRecordIsPositional() {
      assertEquals(
          new Pair(7, "seven"), mapper.fromItf(itf("{\"#tup\": [7, \"seven\"]}"), Pair.class));
    }

    @Test
    void tupleArityMismatchIsAnError() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class, () -> mapper.fromItf(itf("{\"#tup\": [7]}"), Pair.class));
      assertTrue(e.getMessage().contains("2 components"), e.getMessage());
    }

    @Test
    void tupleToList() {
      assertEquals(
          List.of(BigInteger.ONE, "a"),
          mapper.fromItf(itf("{\"#tup\": [1, \"a\"]}"), new TypeReference<List<Object>>() {}));
    }
  }

  // --- collections
  // --------------------------------------------------------------------------------

  @Nested
  class Collections {
    @Test
    void listOfInts() {
      assertEquals(
          List.of(1, 2, 3),
          mapper.fromItf(itf("[1, 2, 3]"), new TypeReference<List<Integer>>() {}));
    }

    @Test
    void setOfStrings() {
      assertEquals(
          Set.of("a", "b"),
          mapper.fromItf(
              itf("{\"#set\": [\"a\", \"b\", \"a\"]}"), new TypeReference<Set<String>>() {}));
    }

    @Test
    void mapWithStringKeys() {
      assertEquals(
          Map.of("a", 1, "b", 2),
          mapper.fromItf(
              itf("{\"#map\": [[\"a\", 1], [\"b\", 2]]}"),
              new TypeReference<Map<String, Integer>>() {}));
    }

    @Test
    void mapWithIntKeys() {
      assertEquals(
          Map.of(1, "one", 2, "two"),
          mapper.fromItf(
              itf("{\"#map\": [[1, \"one\"], [2, \"two\"]]}"),
              new TypeReference<Map<Integer, String>>() {}));
    }

    @Test
    void mapWithRecordKeys() {
      Map<Coord, Player> board =
          mapper.fromItf(
              itf(
                  "{\"#map\": [[{\"row\": 1, \"col\": 1}, {\"tag\": \"X\", \"value\": {\"#tup\": []}}],"
                      + " [{\"#tup\": [2, 2]}, {\"tag\": \"O\", \"value\": {\"#tup\": []}}]]}"),
              new TypeReference<Map<Coord, Player>>() {});
      assertEquals(Map.of(new Coord(1, 1), Player.X, new Coord(2, 2), Player.O), board);
    }

    @Test
    void sortedMapImplementationIsHonoured() {
      SortedMap<Integer, String> sorted =
          mapper.fromItf(
              itf("{\"#map\": [[2, \"b\"], [1, \"a\"]]}"),
              new TypeReference<TreeMap<Integer, String>>() {});
      assertEquals(List.of(1, 2), List.copyOf(sorted.keySet()));
    }

    @Test
    void nestedMaps() {
      Map<Integer, Map<Integer, Square>> board =
          mapper.fromItf(
              itf(
                  "{\"#map\": [[1, {\"#map\": [[1, {\"tag\": \"Empty\", \"value\": {\"#tup\": []}}],"
                      + " [2, {\"tag\": \"Occupied\", \"value\": {\"tag\": \"X\", \"value\": {\"#tup\": []}}}]]}]]}"),
              new TypeReference<Map<Integer, Map<Integer, Square>>>() {});
      assertEquals(Map.of(1, Map.of(1, new Empty(), 2, new Occupied(Player.X))), board);
    }
  }

  // --- sum types
  // ----------------------------------------------------------------------------------

  @Nested
  class SumTypes {
    @Test
    void variantWithoutComponents() {
      assertEquals(
          new Empty(),
          mapper.fromItf(itf("{\"tag\": \"Empty\", \"value\": {\"#tup\": []}}"), Square.class));
    }

    @Test
    void variantWithOneComponent() {
      assertEquals(
          new Occupied(Player.O),
          mapper.fromItf(
              itf(
                  "{\"tag\": \"Occupied\", \"value\": {\"tag\": \"O\", \"value\": {\"#tup\": []}}}"),
              Square.class));
    }

    @Test
    void variantWithRecordValueMapsByFieldName() {
      assertEquals(
          new Move("p1"),
          mapper.fromItf(itf("{\"tag\": \"Move\", \"value\": {\"node\": \"p1\"}}"), Action.class));
    }

    @Test
    void variantWithSeveralComponentsTakesATuple() {
      assertEquals(
          new Pair2(1, "a"),
          mapper.fromItf(
              itf("{\"tag\": \"Pair2\", \"value\": {\"#tup\": [1, \"a\"]}}"), Action.class));
    }

    @Test
    void variantWithSeveralComponentsRejectsOtherValues() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class,
              () -> mapper.fromItf(itf("{\"tag\": \"Pair2\", \"value\": 1}"), Action.class));
      assertTrue(e.getMessage().contains("expected a tuple with 2 entries"), e.getMessage());
    }

    @Test
    void itfVariantAnnotationOverridesTheTag() {
      assertEquals(
          new Named(5), mapper.fromItf(itf("{\"tag\": \"renamed\", \"value\": 5}"), Action.class));
      assertThrows(
          ItfMappingException.class,
          () -> mapper.fromItf(itf("{\"tag\": \"Named\", \"value\": 5}"), Action.class));
    }

    @Test
    void nestedSealedInterfacesAreFlattened() {
      assertEquals(
          new Deep(Optional.of(3)),
          mapper.fromItf(
              itf("{\"tag\": \"Deep\", \"value\": {\"tag\": \"Some\", \"value\": 3}}"),
              Action.class));
    }

    @Test
    void unknownTagIsAnError() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class,
              () ->
                  mapper.fromItf(
                      itf("{\"tag\": \"Full\", \"value\": {\"#tup\": []}}"), Square.class));
      assertTrue(e.getMessage().contains("Unknown variant `Full`"), e.getMessage());
      assertTrue(e.getMessage().contains("[Occupied, Empty]"), e.getMessage());
    }

    @Test
    void nonVariantValueIsAnError() {
      ItfMappingException e =
          assertThrows(ItfMappingException.class, () -> mapper.fromItf(itf("42"), Square.class));
      assertTrue(e.getMessage().contains("expected a variant record"), e.getMessage());
    }

    @Test
    void tagOnlySumTypeToEnum() {
      assertEquals(
          Player.X,
          mapper.fromItf(itf("{\"tag\": \"X\", \"value\": {\"#tup\": []}}"), Player.class));
      assertEquals(Player.O, mapper.fromItf(new ItfValue.Str("O"), Player.class));
    }

    @Test
    void enumRejectsVariantWithValue() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class,
              () -> mapper.fromItf(itf("{\"tag\": \"X\", \"value\": 1}"), Player.class));
      assertTrue(e.getMessage().contains("carries a value"), e.getMessage());
    }

    @Test
    void enumRejectsUnknownConstant() {
      assertThrows(
          ItfMappingException.class,
          () -> mapper.fromItf(itf("{\"tag\": \"Z\", \"value\": {\"#tup\": []}}"), Player.class));
    }
  }

  // --- Option
  // -------------------------------------------------------------------------------------

  @Nested
  class Options {
    @Test
    void someAndNone() {
      assertEquals(
          Optional.of(1),
          mapper.fromItf(
              itf("{\"tag\": \"Some\", \"value\": 1}"), new TypeReference<Optional<Integer>>() {}));
      assertEquals(
          Optional.empty(),
          mapper.fromItf(
              itf("{\"tag\": \"None\", \"value\": {\"#tup\": []}}"),
              new TypeReference<Optional<Integer>>() {}));
    }

    @Test
    void optionalRecordComponents() {
      assertEquals(
          new WithOptional(Optional.of(1), "n"),
          mapper.fromItf(
              itf("{\"maybe\": {\"tag\": \"Some\", \"value\": 1}, \"name\": \"n\"}"),
              WithOptional.class));
      assertEquals(
          new WithOptional(Optional.empty(), "n"),
          mapper.fromItf(
              itf("{\"maybe\": {\"tag\": \"None\", \"value\": {\"#tup\": []}}, \"name\": \"n\"}"),
              WithOptional.class));
    }

    @Test
    void missingOptionalComponentIsEmpty() {
      assertEquals(
          new WithOptional(Optional.empty(), "n"),
          mapper.fromItf(itf("{\"name\": \"n\"}"), WithOptional.class));
    }

    @Test
    void plainValueIsWrapped() {
      assertEquals(
          Optional.of("x"),
          mapper.fromItf(new ItfValue.Str("x"), new TypeReference<Optional<String>>() {}));
    }
  }

  // --- errors
  // -------------------------------------------------------------------------------------

  @Nested
  class Errors {
    @Test
    void unserializableValuesCannotBeMapped() {
      assertThrows(
          ItfMappingException.class,
          () -> mapper.fromItf(itf("{\"#unserializable\": \"Set(Int)\"}"), Set.class));
    }

    @Test
    void messagesNameTheValueAndTheTarget() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class, () -> mapper.fromItf(itf("{\"x\": 1}"), Position.class));
      assertTrue(e.getMessage().startsWith("Cannot map { x: 1 } to "), e.getMessage());
      assertTrue(e.getMessage().contains("Position"), e.getMessage());
    }

    @Test
    void stateFromItfAddsTheUpstreamHint() {
      ItfMappingException e =
          assertThrows(
              ItfMappingException.class,
              () -> mapper.stateFromItf(itf("{\"x\": 1}"), Position.class));
      assertTrue(e.getMessage().startsWith(ItfMappingException.STATE_HINT), e.getMessage());
      assertTrue(e.getMessage().contains("Cannot map { x: 1 }"), e.getMessage());
      assertInstanceOf(ItfMappingException.class, e.getCause());
    }

    @Test
    void stateFromItfPassesThroughOnSuccess() {
      assertEquals(
          new Position(1, 2), mapper.stateFromItf(itf("{\"x\": 1, \"y\": 2}"), Position.class));
    }
  }

  @Test
  void converterForStepPicks() {
    assertEquals(new Position(1, 2), mapper.to(Position.class).apply(itf("{\"x\": 1, \"y\": 2}")));
    assertEquals(
        List.of(1, 2), mapper.to(new TypeReference<List<Integer>>() {}).apply(itf("[1, 2]")));
  }
}
