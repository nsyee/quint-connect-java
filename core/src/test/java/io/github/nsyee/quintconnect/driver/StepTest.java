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

import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.itf.ItfValue;
import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StepTest {

  private static ItfTrace fixture(String name) throws IOException {
    try (InputStream in = StepTest.class.getResourceAsStream("/itf/" + name)) {
      return ItfParser.parse(in);
    }
  }

  private static Step step(String action, NondetPicks picks, ItfValue state) {
    return new Step(action, picks, state);
  }

  @Test
  void requiredPick() {
    Step step = step("A", NondetPicks.of(Record.of("x", Int.of(1))), Record.of());
    assertEquals(Int.of(1), step.pick("x"));
    assertEquals("1", step.pick("x", v -> ((Int) v).value().toString()));
    DriverException e = assertThrows(DriverException.class, () -> step.pick("y"));
    assertEquals("Unknown nondet pick `y`", e.getMessage());
  }

  @Test
  void optionalPick() {
    Step step = step("A", NondetPicks.of(Record.of("x", Int.of(1))), Record.of());
    assertEquals(Optional.of(Int.of(1)), step.optionalPick("x"));
    assertEquals(Optional.empty(), step.optionalPick("y"));
    assertEquals(Optional.of("1"), step.optionalPick("x", v -> ((Int) v).value().toString()));
  }

  @Test
  void unimplementedMessage() {
    Step step = step("Foo", NondetPicks.empty(), Record.of());
    assertEquals("Unimplemented action `Foo`", Step.unimplemented(step).getMessage());
  }

  @Test
  void anonymousAction() {
    assertTrue(step("", NondetPicks.empty(), Record.of()).isAnonymous());
    assertFalse(step("init", NondetPicks.empty(), Record.of()).isAnonymous());
  }

  @Test
  void displayWithPicksAndRecordState() {
    Step step =
        step(
            "MoveX",
            NondetPicks.of(Record.of("coordinate", Tuple.of(Int.of(1), Int.of(1)))),
            Record.of("nextTurn", Record.of("tag", new Str("O"), "value", Tuple.of())));
    assertEquals(
        "Action taken: MoveX\nNondet picks:\n+ coordinate: (1, 1)\nNext state:\n+ nextTurn: O",
        step.toString());
  }

  @Test
  void displayAnonymousWithoutPicksAndEmptyState() {
    Step step = step("", NondetPicks.empty(), Record.of());
    assertEquals(
        "Action taken: <anonymous>\nNondet picks: <none>\nNext state: <none>", step.toString());
  }

  @Test
  void displayMapAndScalarState() {
    Step map =
        step(
            "A",
            NondetPicks.empty(),
            ItfValue.Map.of(new ItfValue.Map.Entry(Int.of(1), new Str("a"))));
    assertEquals("Action taken: A\nNondet picks: <none>\nNext state:\n+ 1: \"a\"", map.toString());
    Step emptyMap = step("A", NondetPicks.empty(), ItfValue.Map.of());
    assertTrue(emptyMap.toString().endsWith("Next state: <none>"));
    Step scalar = step("A", NondetPicks.empty(), Int.of(3));
    assertTrue(scalar.toString().endsWith("Next state: 3"));
  }

  @Test
  void fromTicTacToeFixtureUsesMbtVars() throws IOException {
    ItfTrace trace = fixture("tictactoe.itf.json");
    Step init = Step.from(trace.states().get(0), DriverConfig.DEFAULT);
    assertEquals("init", init.action());
    assertTrue(init.picks().isEmpty());
    Record state = (Record) init.specState();
    assertFalse(state.has("mbt::actionTaken"));
    assertFalse(state.has("mbt::nondetPicks"));
    assertTrue(state.has("board"));
    assertTrue(state.has("nextTurn"));

    Step second = Step.from(trace.states().get(1), DriverConfig.DEFAULT);
    assertTrue(second.action().startsWith("Move"), second.action());
    assertFalse(second.picks().isEmpty());
  }

  @Test
  void fromTwoPhaseCommitFixtureUsesSumType() throws IOException {
    ItfTrace trace = fixture("two_phase_commit.itf.json");
    DriverConfig config =
        DriverConfig.statePath("two_phase_commit::choreo::s", "system")
            .nondetPath("two_phase_commit::choreo::s", "extensions", "actionTaken");
    Step init = Step.from(trace.states().get(0), config);
    assertEquals("Init", init.action());
    assertTrue(init.picks().isEmpty());
    assertTrue(init.specState() instanceof ItfValue.Map);

    Step second = Step.from(trace.states().get(1), config);
    assertEquals("SpontaneouslyPrepares", second.action());
    assertEquals(new Str("p1"), second.pick("node"));
  }
}
