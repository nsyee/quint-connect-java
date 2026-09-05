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

import java.util.Optional;

/**
 * A plain tic-tac-toe game: the "system under test" of the example, written without any knowledge
 * of Quint. The board is a 3x3 array indexed {@code [y][x]} from zero, X moves first.
 */
public final class TicTacToe {

  /** The two players. */
  public enum Player {
    /** Moves first. */
    X,
    /** Moves second. */
    O;

    /** The player moving after this one. */
    public Player other() {
      return this == X ? O : X;
    }
  }

  /** A zero-based board position. */
  public record Position(int x, int y) {}

  private final Player[][] board = new Player[3][3];
  private Player nextTurn = Player.X;

  /** Places {@code player} at {@code pos}; fails if it is not their turn or the cell is taken. */
  public void moveTo(Position pos, Player player) {
    if (nextTurn != player) {
      throw new IllegalStateException("player out of turn");
    }
    if (board[pos.y()][pos.x()] != null) {
      throw new IllegalStateException("moving to occupied cell");
    }
    board[pos.y()][pos.x()] = player;
    nextTurn = player.other();
  }

  /** The player occupying {@code pos}, if any. */
  public Optional<Player> at(Position pos) {
    return Optional.ofNullable(board[pos.y()][pos.x()]);
  }

  /** The player whose turn it is. */
  public Player nextTurn() {
    return nextTurn;
  }
}
