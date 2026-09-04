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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfValue.Bool;
import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.List;
import io.github.nsyee.quintconnect.itf.ItfValue.Map;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Set;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import io.github.nsyee.quintconnect.itf.ItfValue.Unserializable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItfParserTest {

  @Test
  void scalars() {
    assertEquals(Bool.TRUE, ItfParser.parseValue("true"));
    assertEquals(Int.of(42), ItfParser.parseValue("42"));
    assertEquals(Int.of(-3), ItfParser.parseValue("-3"));
    assertEquals(new Str("foo"), ItfParser.parseValue("\"foo\""));
    assertEquals(
        Int.of("123456789012345678901234567890"),
        ItfParser.parseValue("{\"#bigint\": \"123456789012345678901234567890\"}"));
    assertEquals(Int.of(-5), ItfParser.parseValue("{\"#bigint\": \"-5\"}"));
  }

  @Test
  void collections() {
    assertEquals(List.of(Int.of(1), Bool.FALSE), ItfParser.parseValue("[1, false]"));
    assertEquals(Tuple.of(Int.of(1), new Str("a")), ItfParser.parseValue("{\"#tup\": [1, \"a\"]}"));
    assertEquals(Tuple.of(), ItfParser.parseValue("{\"#tup\": []}"));
    assertEquals(Set.of(Int.of(1), Int.of(2)), ItfParser.parseValue("{\"#set\": [2, 1]}"));
    assertEquals(
        Map.of(new Map.Entry(Int.of(1), new Str("a")), new Map.Entry(Int.of(2), new Str("b"))),
        ItfParser.parseValue("{\"#map\": [[1, \"a\"], [2, \"b\"]]}"));
    assertEquals(new Unserializable("Nat"), ItfParser.parseValue("{\"#unserializable\": \"Nat\"}"));
  }

  @Test
  void records() {
    assertEquals(
        Record.of("a", Int.of(1), "b", Bool.TRUE), ItfParser.parseValue("{\"a\": 1, \"b\": true}"));
    assertEquals(Record.of(), ItfParser.parseValue("{}"));
    // A `#…` key next to other keys is just a field name.
    assertEquals(
        Record.of("#tup", Int.of(1), "x", Int.of(2)),
        ItfParser.parseValue("{\"#tup\": 1, \"x\": 2}"));
    // Sum-type variants stay records.
    ItfValue variant = ItfParser.parseValue("{\"tag\": \"Some\", \"value\": 1}");
    assertEquals(Record.of("tag", new Str("Some"), "value", Int.of(1)), variant);
    assertEquals(Optional.of("Some"), ((Record) variant).variantTag());
  }

  @Test
  void malformedSpecialObjects() {
    assertParseFails("{\"#bigint\": \"abc\"}", "Invalid `#bigint` literal: abc");
    assertParseFails("{\"#bigint\": true}", "Expected `#bigint` to hold a decimal string");
    assertParseFails("{\"#tup\": 1}", "Expected `#tup` to hold an array");
    assertParseFails("{\"#set\": {}}", "Expected `#set` to hold an array");
    assertParseFails("{\"#map\": [[1]]}", "Expected `#map` entries to be [key, value] pairs");
    assertParseFails("{\"#map\": [1]}", "Expected `#map` entries to be [key, value] pairs");
    assertParseFails("{\"#unserializable\": 1}", "Expected `#unserializable` to hold a string");
    assertParseFails("null", "Unsupported JSON node in ITF value: NULL");
    assertParseFails("1.5", "Non-integer numbers are not valid ITF values: 1.5");
    assertParseFails("[1,", "Invalid JSON");
  }

  @Test
  void traceStructure() {
    ItfTrace trace =
        ItfParser.parse(
            """
            {
              "#meta": {
                "format": "ITF",
                "format-description": "https://apalache-mc.org/docs/adr/015adr-trace.html",
                "source": "spec.qnt",
                "status": "ok",
                "description": "d",
                "timestamp": 1788524911230,
                "varTypes": { "x": "Int", "ignored": 1 },
                "extra": [1, 2]
              },
              "vars": ["x"],
              "states": [
                { "#meta": { "index": 0 }, "x": 1 },
                { "#meta": { "index": 1 }, "x": { "#bigint": "2" } }
              ],
              "loop": 1
            }
            """);
    assertEquals(Optional.of("ITF"), trace.meta().format());
    assertEquals(
        Optional.of("https://apalache-mc.org/docs/adr/015adr-trace.html"),
        trace.meta().formatDescription());
    assertEquals(Optional.of("spec.qnt"), trace.meta().source());
    assertEquals(Optional.of("ok"), trace.meta().status());
    assertEquals(Optional.of("d"), trace.meta().description());
    assertEquals(1788524911230L, trace.meta().timestamp().orElseThrow());
    assertEquals(java.util.Map.of("x", "Int"), trace.meta().varTypes());
    assertEquals(java.util.List.of("x"), trace.vars());
    assertEquals(2, trace.length());
    assertEquals(new ItfState(0, Record.of("x", Int.of(1))), trace.states().get(0));
    assertEquals(new ItfState(1, Record.of("x", Int.of(2))), trace.states().get(1));
    assertEquals(OptionalInt.of(1), trace.loop());
  }

  @Test
  void minimalTrace() {
    ItfTrace trace = ItfParser.parse("{\"states\": [{\"x\": 1}, {\"x\": 2}]}");
    assertEquals(ItfMeta.EMPTY, trace.meta());
    assertEquals(java.util.List.of(), trace.vars());
    assertEquals(OptionalInt.empty(), trace.loop());
    assertEquals(java.util.List.of(0, 1), trace.states().stream().map(ItfState::index).toList());
  }

  @Test
  void malformedTraces() {
    assertTraceFails("[]", "Expected a JSON object at the top level of an ITF trace");
    assertTraceFails("{}", "Expected `states` to be an array");
    assertTraceFails("{\"states\": [1]}", "Expected state 0 to be an object");
    assertTraceFails(
        "{\"states\": [], \"vars\": [1]}", "Expected `vars` to be an array of strings");
    assertTraceFails("{\"states\": [], \"#meta\": 1}", "Expected `#meta` to be an object");
    assertTraceFails("{\"states\": [], \"loop\": \"x\"}", "Expected `loop` to be an integer");
  }

  @Test
  void tictactoeFixture() throws IOException {
    ItfTrace trace = parseFixture("tictactoe.itf.json");
    assertEquals(Optional.of("ITF"), trace.meta().format());
    assertEquals(Optional.of("ok"), trace.meta().status());
    assertEquals(
        java.util.List.of("nextTurn", "board", "mbt::actionTaken", "mbt::nondetPicks"),
        trace.vars());
    assertEquals(6, trace.length());
    assertEquals(OptionalInt.empty(), trace.loop());

    ItfState first = trace.states().get(0);
    assertEquals(0, first.index());
    assertEquals(Optional.of(new Str("init")), first.value().get("mbt::actionTaken"));
    assertEquals("X", first.value().get("nextTurn").orElseThrow().display());

    ItfState second = trace.states().get(1);
    assertEquals(1, second.index());
    assertEquals(Optional.of(new Str("MoveX")), second.value().get("mbt::actionTaken"));
    Record picks = (Record) second.value().get("mbt::nondetPicks").orElseThrow();
    assertEquals(
        Optional.of(Tuple.of(Int.of(3), Int.of(3))),
        ItfValues.asOption(picks.get("corner").orElseThrow()));
    assertEquals(Optional.empty(), ItfValues.asOption(picks.get("coordinate").orElseThrow()));

    Map board = (Map) second.value().get("board").orElseThrow();
    Map row3 = (Map) board.get(Int.of(3)).orElseThrow();
    assertEquals("Occupied(X)", row3.get(Int.of(3)).orElseThrow().display());
    assertEquals("Empty", row3.get(Int.of(1)).orElseThrow().display());

    for (int i = 0; i < trace.length(); i++) {
      assertEquals(i, trace.states().get(i).index());
      assertEquals(
          java.util.Set.copyOf(trace.vars()), trace.states().get(i).value().fields().keySet());
    }
  }

  @Test
  void twoPhaseCommitFixture() throws IOException {
    ItfTrace trace = parseFixture("two_phase_commit.itf.json");
    assertEquals(Optional.of("passed"), trace.meta().status());
    assertEquals(java.util.List.of("two_phase_commit::choreo::s"), trace.vars());
    assertEquals(8, trace.length());

    Record s =
        (Record) trace.states().get(1).value().get("two_phase_commit::choreo::s").orElseThrow();
    Record extensions = (Record) s.get("extensions").orElseThrow();
    assertEquals(
        "SpontaneouslyPrepares({ node: \"p1\" })",
        extensions.get("actionTaken").orElseThrow().display());

    Map system = (Map) s.get("system").orElseThrow();
    Record p1 = (Record) system.get(new Str("p1")).orElseThrow();
    assertEquals(
        Record.of(
            "process_id",
            new Str("p1"),
            "role",
            Record.of("tag", new Str("Participant"), "value", Tuple.of()),
            "stage",
            Record.of("tag", new Str("Prepared"), "value", Tuple.of())),
        p1);

    Map messages = (Map) s.get("messages").orElseThrow();
    assertEquals(
        Set.of(Record.of("tag", new Str("ParticipantPrepared"), "value", new Str("p1"))),
        messages.get(new Str("c")).orElseThrow());
    assertEquals(
        "Set(ParticipantPrepared(\"p1\"))", messages.get(new Str("c")).orElseThrow().display());
  }

  @Test
  void fixturesRoundTripThroughEveryEntryPoint(@TempDir Path dir) throws IOException {
    for (String name : java.util.List.of("tictactoe.itf.json", "two_phase_commit.itf.json")) {
      byte[] bytes;
      try (InputStream in = fixture(name)) {
        bytes = in.readAllBytes();
      }
      Path file = dir.resolve(name);
      Files.write(file, bytes);
      ItfTrace fromPath = ItfParser.parse(file);
      ItfTrace fromString =
          ItfParser.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
      assertEquals(fromString, fromPath);
      assertEquals(fromString.hashCode(), fromPath.hashCode());
    }
  }

  @Test
  void pathErrorsCarryTheFileName(@TempDir Path dir) throws IOException {
    Path missing = dir.resolve("missing.itf.json");
    UncheckedIOException io =
        assertThrows(UncheckedIOException.class, () -> ItfParser.parse(missing));
    assertEquals("Can't open trace file at: " + missing, io.getMessage());

    Path broken = dir.resolve("broken.itf.json");
    Files.writeString(broken, "{");
    ItfParseException parse = assertThrows(ItfParseException.class, () -> ItfParser.parse(broken));
    assertEquals("Failed to parse JSON trace file at: " + broken, parse.getMessage());
    assertInstanceOf(ItfParseException.class, parse.getCause());
  }

  private static ItfTrace parseFixture(String name) throws IOException {
    try (InputStream in = fixture(name)) {
      return ItfParser.parse(in);
    }
  }

  private static InputStream fixture(String name) {
    InputStream in = ItfParserTest.class.getResourceAsStream("/itf/" + name);
    assertTrue(in != null, "missing fixture " + name);
    return in;
  }

  private static void assertParseFails(String json, String messagePrefix) {
    ItfParseException e =
        assertThrows(ItfParseException.class, () -> ItfParser.parseValue(json), json);
    assertTrue(
        e.getMessage().startsWith(messagePrefix),
        () -> "expected [" + e.getMessage() + "] to start with [" + messagePrefix + "]");
  }

  private static void assertTraceFails(String json, String message) {
    ItfParseException e = assertThrows(ItfParseException.class, () -> ItfParser.parse(json), json);
    assertEquals(message, e.getMessage());
  }
}
