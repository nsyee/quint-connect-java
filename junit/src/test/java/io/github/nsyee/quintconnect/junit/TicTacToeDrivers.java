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
package io.github.nsyee.quintconnect.junit;

import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.itf.mapper.ItfMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal tic-tac-toe implementation and drivers used by the extension tests. */
final class TicTacToeDrivers {

  private TicTacToeDrivers() {}

  enum Player {
    X,
    O
  }

  sealed interface Square permits Occupied, Empty {}

  record Occupied(Player player) implements Square {}

  record Empty() implements Square {}

  record GameState(Player nextTurn, Map<Integer, Map<Integer, Square>> board) {}

  record Coordinate(int row, int col) {}

  static final class Game {
    final Map<Integer, Map<Integer, Square>> board = new HashMap<>();
    Player nextTurn = Player.X;

    Game() {
      for (int r = 1; r <= 3; r++) {
        Map<Integer, Square> row = new HashMap<>();
        for (int c = 1; c <= 3; c++) {
          row.put(c, new Empty());
        }
        board.put(r, row);
      }
    }

    void move(Player player, Coordinate at) {
      board.get(at.row()).put(at.col(), new Occupied(player));
      nextTurn = player == Player.X ? Player.O : Player.X;
    }
  }

  /** Follows the specification faithfully. */
  static class Correct implements Driver<GameState> {
    private final ItfMapper mapper = ItfMapper.defaultMapper();
    final List<String> actions = new ArrayList<>();
    Game game = new Game();

    @Override
    public void step(Step step) throws Exception {
      actions.add(step.action());
      switch (step.action()) {
        case "init" -> game = new Game();
        case "MoveX" -> {
          Coordinate at =
              step.optionalPick("corner", mapper.to(Coordinate.class))
                  .or(() -> step.optionalPick("coordinate", mapper.to(Coordinate.class)))
                  .orElse(new Coordinate(2, 2));
          game.move(Player.X, at);
        }
        case "MoveO" -> game.move(Player.O, step.pick("coordinate", mapper.to(Coordinate.class)));
        default -> throw Step.unimplemented(step);
      }
    }

    @Override
    public GameState state() {
      return new GameState(game.nextTurn, game.board);
    }

    @Override
    public Class<GameState> stateType() {
      return GameState.class;
    }
  }

  /** Forgets to switch turns after O moves, so the state diverges at step 2. */
  static final class Diverging extends Correct {
    @Override
    public void step(Step step) throws Exception {
      super.step(step);
      if (step.action().equals("MoveO")) {
        game.nextTurn = Player.O;
      }
    }
  }

  /** Throws on the first {@code MoveO}. */
  static final class Throwing extends Correct {
    @Override
    public void step(Step step) throws Exception {
      if (step.action().equals("MoveO")) {
        throw new java.io.IOException("boom");
      }
      super.step(step);
    }
  }

  /** Accepts every action and skips state checking. */
  static final class Stateless implements Driver<Void> {
    final List<String> actions = new ArrayList<>();

    @Override
    public void step(Step step) {
      actions.add(step.action());
    }

    @Override
    public Void state() {
      return null;
    }

    @Override
    public Class<Void> stateType() {
      return Void.class;
    }
  }
}
