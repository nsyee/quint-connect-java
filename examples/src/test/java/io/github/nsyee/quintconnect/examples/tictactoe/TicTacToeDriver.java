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
package io.github.nsyee.quintconnect.examples.tictactoe;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.examples.tictactoe.TicTacToe.Player;
import io.github.nsyee.quintconnect.examples.tictactoe.TicTacToe.Position;
import io.github.nsyee.quintconnect.itf.mapper.ItfMapper;
import java.util.Map;
import java.util.TreeMap;

/**
 * Connects {@link TicTacToe} to {@code spec/tictactoe.qnt}.
 *
 * <p>The specification tracks the board as {@code int -> (int -> Square)} with 1-based coordinates,
 * so the abstraction function ({@link #state()}) re-indexes the game's zero-based array; the driver
 * translates the picked coordinates the other way round.
 */
final class TicTacToeDriver implements Driver<TicTacToeDriver.GameState> {

  /** {@code type Square = Occupied(Player) | Empty}. */
  sealed interface Square permits Occupied, Empty {}

  record Occupied(Player player) implements Square {}

  record Empty() implements Square {}

  /** The specification's state variables; {@code nextTurn} is renamed to Java style. */
  record GameState(
      Map<Integer, Map<Integer, Square>> board, @JsonProperty("nextTurn") Player nextTurn) {}

  /** A 1-based {@code (int, int)} coordinate of the specification. */
  record Coordinate(int x, int y) {
    Position toPosition() {
      return new Position(x - 1, y - 1);
    }
  }

  private final ItfMapper mapper = ItfMapper.defaultMapper();
  private TicTacToe game = new TicTacToe();

  @Override
  public void step(Step step) {
    switch (step.action()) {
      case "init" -> game = new TicTacToe();
      case "MoveX" -> {
        // `corner` and `coordinate` are picked by different sub-actions of MoveX; when neither is
        // present the spec moved to the centre.
        Position pos =
            step.optionalPick("corner", mapper.to(Coordinate.class))
                .or(() -> step.optionalPick("coordinate", mapper.to(Coordinate.class)))
                .map(Coordinate::toPosition)
                .orElse(new Position(1, 1));
        game.moveTo(pos, Player.X);
      }
      case "MoveO" ->
          game.moveTo(step.pick("coordinate", mapper.to(Coordinate.class)).toPosition(), Player.O);
      case "stuttered" -> {}
      default -> throw Step.unimplemented(step);
    }
  }

  @Override
  public GameState state() {
    Map<Integer, Map<Integer, Square>> board = new TreeMap<>();
    for (int x = 1; x <= 3; x++) {
      Map<Integer, Square> column = new TreeMap<>();
      for (int y = 1; y <= 3; y++) {
        column.put(
            y,
            game.at(new Coordinate(x, y).toPosition())
                .<Square>map(Occupied::new)
                .orElse(new Empty()));
      }
      board.put(x, column);
    }
    return new GameState(board, game.nextTurn());
  }

  @Override
  public Class<GameState> stateType() {
    return GameState.class;
  }
}
