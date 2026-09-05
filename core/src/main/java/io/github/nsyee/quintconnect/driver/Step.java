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

import io.github.nsyee.quintconnect.itf.ItfState;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One step of a trace: the action Quint took, the nondeterministic picks it made and the
 * specification state it arrived at.
 *
 * <p>Steps are handed to {@link Driver#step}. Drivers usually {@code switch} on {@link #action()}
 * and read picks with {@link #pick(String)} / {@link #optionalPick(String)}, or use the {@link
 * Actions} dispatcher.
 */
public final class Step {

  private final String action;
  private final NondetPicks picks;
  private final ItfValue specState;

  /**
   * Creates a step.
   *
   * @param action the action name; empty for anonymous actions
   * @param picks the nondet picks
   * @param specState the specification state after the action, already navigated to {@link
   *     DriverConfig#statePath()}
   */
  public Step(String action, NondetPicks picks, ItfValue specState) {
    this.action = Objects.requireNonNull(action, "action");
    this.picks = Objects.requireNonNull(picks, "picks");
    this.specState = Objects.requireNonNull(specState, "specState");
  }

  /**
   * Extracts a step from a trace state according to {@code config}.
   *
   * @throws DriverException if the state lacks the expected variables or paths
   */
  public static Step from(ItfState state, DriverConfig config) {
    return StepExtractor.extract(state.value(), config);
  }

  /** The action name, or an empty string for an anonymous action. */
  public String action() {
    return action;
  }

  /** Whether the action has no name (a bare {@code any} / {@code all} in {@code step}). */
  public boolean isAnonymous() {
    return action.isEmpty();
  }

  /** All nondet picks of this step. */
  public NondetPicks picks() {
    return picks;
  }

  /**
   * Returns the pick named {@code name}.
   *
   * @throws DriverException if the variable was not picked in this step
   */
  public ItfValue pick(String name) {
    return picks.get(name).orElseThrow(() -> unknownPick(name));
  }

  /**
   * Returns the pick named {@code name} converted with {@code converter}.
   *
   * @throws DriverException if the variable was not picked in this step
   */
  public <T> T pick(String name, Function<? super ItfValue, ? extends T> converter) {
    return converter.apply(pick(name));
  }

  /** Returns the pick named {@code name}, if the variable was picked in this step. */
  public Optional<ItfValue> optionalPick(String name) {
    return picks.get(name);
  }

  /** Returns the pick named {@code name} converted with {@code converter}, if present. */
  public <T> Optional<T> optionalPick(
      String name, Function<? super ItfValue, ? extends T> converter) {
    return picks.get(name).map(converter);
  }

  /** The specification state after this step, navigated to {@link DriverConfig#statePath()}. */
  public ItfValue specState() {
    return specState;
  }

  /** The exception a driver should throw for an action it does not handle. */
  public static DriverException unimplemented(Step step) {
    return new DriverException("Unimplemented action `" + step.action + "`");
  }

  static DriverException unknownPick(String name) {
    return new DriverException("Unknown nondet pick `" + name + "`");
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Step that
        && action.equals(that.action)
        && picks.equals(that.picks)
        && specState.equals(that.specState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(action, picks, specState);
  }

  /**
   * Renders the step as the Rust crate does:
   *
   * <pre>
   * Action taken: MoveX
   * Nondet picks:
   * + coordinate: (1, 1)
   * Next state:
   * + board: Map(...)
   * + nextTurn: X
   * </pre>
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Action taken:");
    sb.append(action.isEmpty() ? " <anonymous>" : " " + action).append('\n');

    sb.append("Nondet picks:");
    if (picks.isEmpty()) {
      sb.append(" <none>");
    } else {
      sb.append('\n').append(picks);
    }
    sb.append('\n');

    sb.append("Next state:");
    switch (specState) {
      case ItfValue.Record rec -> {
        if (rec.size() == 0) {
          sb.append(" <none>");
        } else {
          for (Map.Entry<String, ItfValue> field : rec.fields().entrySet()) {
            sb.append("\n+ ")
                .append(field.getKey())
                .append(": ")
                .append(field.getValue().display());
          }
        }
      }
      case ItfValue.Map map -> {
        if (map.entries().isEmpty()) {
          sb.append(" <none>");
        } else {
          for (ItfValue.Map.Entry entry : map.entries()) {
            sb.append("\n+ ")
                .append(entry.key().display())
                .append(": ")
                .append(entry.value().display());
          }
        }
      }
      default -> sb.append(' ').append(specState.display());
    }
    return sb.toString();
  }
}
