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

/**
 * Connects an implementation to a Quint specification.
 *
 * <p>The framework generates traces from the specification and, for every state of every trace,
 * calls {@link #step} with the action that led to that state. After each step it compares {@link
 * #state()} with the specification state deserialised into {@link #stateType()}.
 *
 * <pre>{@code
 * final class TicTacToeDriver implements Driver<GameState> {
 *   private TicTacToe game = new TicTacToe();
 *
 *   @Override
 *   public void step(Step step) {
 *     switch (step.action()) {
 *       case "init" -> game = new TicTacToe();
 *       case "MoveX" -> game.move(Player.X, position(step.pick("coordinate")));
 *       default -> throw Step.unimplemented(step);
 *     }
 *   }
 *
 *   @Override
 *   public GameState state() {
 *     return new GameState(game.board(), game.nextTurn());
 *   }
 *
 *   @Override
 *   public Class<GameState> stateType() {
 *     return GameState.class;
 *   }
 * }
 * }</pre>
 *
 * <p>Use {@code Driver<Void>} with {@code stateType() == Void.class} for stateless drivers; state
 * checking is then skipped, like {@code type State = ()} in the Rust crate.
 *
 * @param <S> the specification state type, produced by the abstraction function {@link #state()}
 */
public interface Driver<S> {

  /**
   * Executes one trace step against the implementation.
   *
   * <p>Implementations typically {@code switch} on {@link Step#action()} and read picks with {@link
   * Step#pick(String)}; unknown actions should fail with {@link Step#unimplemented(Step)}. Any
   * exception aborts the run and is reported with the trace and step index.
   */
  void step(Step step) throws Exception;

  /** Abstraction function: maps the implementation state to the specification state. */
  S state();

  /** Type token used to deserialise the specification state. */
  Class<S> stateType();

  /** Where to find the state and the nondet picks in the ITF state record. */
  default DriverConfig config() {
    return DriverConfig.DEFAULT;
  }
}
