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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfState;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Maps the state shapes of the example specifications ({@code tictactoe} and {@code
 * two_phase_commit}) from the ITF fixtures, the way a user-written driver would.
 */
class ExampleStatesTest {

  private final ItfMapper mapper = ItfMapper.defaultMapper();

  private static ItfTrace fixture(String name) {
    return ItfParser.parse(
        ExampleStatesTest.class.getResourceAsStream("/itf/" + name + ".itf.json"));
  }

  // --- tictactoe
  // ----------------------------------------------------------------------------------

  enum Player {
    X,
    O
  }

  sealed interface Square permits Occupied, Empty {}

  record Occupied(Player player) implements Square {}

  record Empty() implements Square {}

  record GameState(Player nextTurn, Map<Integer, Map<Integer, Square>> board) {}

  record Coordinate(int row, int col) {}

  record TicTacToePicks(
      Optional<Coordinate> coordinate, Optional<Coordinate> corner, Optional<String> pattern) {}

  @Test
  void gameStateFromEveryFixtureState() {
    ItfTrace trace = fixture("tictactoe");
    for (ItfState state : trace.states()) {
      GameState game = mapper.stateFromItf(state.value(), GameState.class);
      assertNotNull(game.nextTurn());
      assertEquals(Set.of(1, 2, 3), game.board().keySet());
      game.board().values().forEach(row -> assertEquals(Set.of(1, 2, 3), row.keySet()));
    }
    GameState first = mapper.stateFromItf(trace.states().get(1).value(), GameState.class);
    assertEquals(Player.O, first.nextTurn());
    assertEquals(new Occupied(Player.X), first.board().get(3).get(3));
    assertEquals(new Empty(), first.board().get(1).get(1));
  }

  @Test
  void nondetPicksFromTicTacToe() {
    ItfValue picks = fixture("tictactoe").states().get(1).value().fields().get("mbt::nondetPicks");
    TicTacToePicks mapped = mapper.fromItf(picks, TicTacToePicks.class);
    assertEquals(
        new TicTacToePicks(Optional.empty(), Optional.of(new Coordinate(3, 3)), Optional.empty()),
        mapped);
  }

  @Test
  void gameStateRoundTripsThroughToItf() {
    ItfValue.Record spec = fixture("tictactoe").states().get(1).value();
    GameState game = mapper.stateFromItf(spec, GameState.class);
    ItfValue back = mapper.toItf(game);
    ItfValue.Record expected =
        ItfValue.Record.of(
            "nextTurn", spec.fields().get("nextTurn"), "board", spec.fields().get("board"));
    assertEquals(expected, back);
  }

  // --- two_phase_commit
  // ---------------------------------------------------------------------------

  enum Role {
    Coordinator,
    Participant
  }

  enum Stage {
    Working,
    Prepared,
    Committed
  }

  record LocalState(@JsonProperty("process_id") String processId, Role role, Stage stage) {}

  sealed interface Message permits ParticipantPrepared, CoordinatorCommit {}

  record ParticipantPrepared(String participant) implements Message {}

  record CoordinatorCommit() implements Message {}

  sealed interface Action
      permits Init, SpontaneouslyPrepares, DecidesOnCommit, CommitsAsInstructed {}

  record Init() implements Action {}

  @ItfVariant(record = true)
  record SpontaneouslyPrepares(String node) implements Action {}

  @ItfVariant(record = true)
  record DecidesOnCommit(String node) implements Action {}

  @ItfVariant(record = true)
  record CommitsAsInstructed(String node) implements Action {}

  record Extensions(Action actionTaken) {}

  record SpecState(
      Map<String, LocalState> system,
      Map<String, Set<Message>> messages,
      Map<String, Set<Message>> events,
      Extensions extensions) {}

  @Test
  void specStateFromEveryFixtureState() {
    ItfTrace trace = fixture("two_phase_commit");
    List<Action> actions =
        trace.states().stream()
            .map(s -> s.value().fields().get("two_phase_commit::choreo::s"))
            .map(s -> mapper.stateFromItf(s, SpecState.class))
            .map(s -> s.extensions().actionTaken())
            .toList();
    assertEquals(new Init(), actions.get(0));
    assertEquals(new SpontaneouslyPrepares("p1"), actions.get(1));
    assertTrue(actions.stream().anyMatch(a -> a instanceof DecidesOnCommit));
    assertTrue(actions.stream().anyMatch(a -> a instanceof CommitsAsInstructed));
  }

  @Test
  void specStateDetails() {
    ItfValue raw =
        fixture("two_phase_commit")
            .states()
            .get(1)
            .value()
            .fields()
            .get("two_phase_commit::choreo::s");
    SpecState state = mapper.stateFromItf(raw, SpecState.class);
    assertEquals(Set.of("c", "p1", "p2", "p3"), state.system().keySet());
    assertEquals(new LocalState("c", Role.Coordinator, Stage.Working), state.system().get("c"));
    assertEquals(new LocalState("p1", Role.Participant, Stage.Prepared), state.system().get("p1"));
    assertEquals(Set.of(new ParticipantPrepared("p1")), state.messages().get("p2"));
    assertEquals(Set.of(), state.events().get("p1"));
  }

  @Test
  void specStateRoundTripsThroughToItf() {
    ItfValue raw =
        fixture("two_phase_commit")
            .states()
            .get(1)
            .value()
            .fields()
            .get("two_phase_commit::choreo::s");
    SpecState state = mapper.stateFromItf(raw, SpecState.class);
    assertEquals(raw, mapper.toItf(state));
  }
}
