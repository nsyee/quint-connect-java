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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent action dispatcher, the Java counterpart of the Rust {@code switch!} macro.
 *
 * <pre>{@code
 * Actions.on(step)
 *     .action("init", () -> game = new TicTacToe())
 *     .action("MoveO", s -> game.move(Player.O, position(s.pick("coordinate"))))
 *     .ignore("stuttered")
 *     .run();
 * }</pre>
 *
 * <p>{@link #run()} executes the handler registered for {@link Step#action()}; an action without a
 * handler fails with {@link Step#unimplemented(Step)} unless {@link #otherwise} was given, so
 * forgetting an action is always reported. Registering the same action twice is an error.
 */
public final class Actions {

  /** A handler that receives the step. */
  @FunctionalInterface
  public interface Handler {
    /** Handles {@code step}. */
    void handle(Step step) throws Exception;
  }

  /** A handler that does not need the step. */
  @FunctionalInterface
  public interface Body {
    /** Runs the handler. */
    void run() throws Exception;
  }

  private static final Handler IGNORE = step -> {};

  private final Step step;
  private final Map<String, Handler> handlers = new LinkedHashMap<>();
  private Handler fallback;

  private Actions(Step step) {
    this.step = Objects.requireNonNull(step, "step");
  }

  /** Starts dispatching {@code step}. */
  public static Actions on(Step step) {
    return new Actions(step);
  }

  /** Registers {@code handler} for {@code action}. */
  public Actions action(String action, Handler handler) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(handler, "handler");
    if (handlers.putIfAbsent(action, handler) != null) {
      throw new IllegalArgumentException("Duplicate handler for action `" + action + "`");
    }
    return this;
  }

  /** Registers {@code body} for {@code action}. */
  public Actions action(String action, Body body) {
    Objects.requireNonNull(body, "body");
    return action(action, step -> body.run());
  }

  /** Registers the given actions as no-ops. */
  public Actions ignore(String... actions) {
    for (String action : actions) {
      action(action, IGNORE);
    }
    return this;
  }

  /** Registers a handler for every action not matched by {@link #action} or {@link #ignore}. */
  public Actions otherwise(Handler handler) {
    Objects.requireNonNull(handler, "handler");
    if (fallback != null) {
      throw new IllegalStateException("otherwise() may only be called once");
    }
    fallback = handler;
    return this;
  }

  /**
   * Runs the handler registered for the step's action.
   *
   * @throws DriverException if no handler matches and no {@link #otherwise} fallback was given
   * @throws Exception whatever the handler throws
   */
  public void run() throws Exception {
    Handler handler = handlers.get(step.action());
    if (handler == null) {
      handler = fallback;
    }
    if (handler == null) {
      throw Step.unimplemented(step);
    }
    handler.handle(step);
  }
}
