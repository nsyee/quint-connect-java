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

import io.github.nsyee.quintconnect.itf.ItfValue;
import java.util.List;

/**
 * Turns an ITF state record into a {@link Step}.
 *
 * <p>Two modes, selected by {@link DriverConfig#nondetPath()}:
 *
 * <ul>
 *   <li><b>mbt vars</b> (empty path): {@code mbt::actionTaken} and {@code mbt::nondetPicks} are
 *       removed from the record and provide the action and the picks; the remainder, navigated by
 *       {@link DriverConfig#statePath()}, is the specification state.
 *   <li><b>sum type</b>: the record at {@code nondetPath} is a variant {@code { tag, value }};
 *       {@code tag} is the action and {@code value} (the unit tuple or a record) holds the picks.
 *       The {@code mbt::*} variables are stripped if present.
 * </ul>
 *
 * <p>Error messages match the Rust crate ({@code driver/step.rs}).
 */
public final class StepExtractor {

  /** Name of the variable Quint uses for the action taken under {@code --mbt}. */
  public static final String MBT_ACTION_TAKEN = "mbt::actionTaken";

  /** Name of the variable Quint uses for the nondet picks under {@code --mbt}. */
  public static final String MBT_NONDET_PICKS = "mbt::nondetPicks";

  private StepExtractor() {}

  /** Extracts a step from {@code state} according to {@code config}. */
  public static Step extract(ItfValue.Record state, DriverConfig config) {
    return config.usesMbtVars()
        ? extractFromMbtVars(state, config.statePath())
        : extractFromSumType(state, config.nondetPath(), config.statePath());
  }

  static Step extractFromMbtVars(ItfValue.Record state, List<String> statePath) {
    String action = extractActionFromMbtVar(state);
    state = state.without(MBT_ACTION_TAKEN);
    NondetPicks picks = extractNondetFromMbtVar(state);
    state = state.without(MBT_NONDET_PICKS);
    return new Step(action, picks, extractValueInPath(state, statePath));
  }

  static Step extractFromSumType(
      ItfValue.Record state, List<String> sumTypePath, List<String> statePath) {
    ItfValue.Record sumType = findRecordInPath(state, sumTypePath);
    String action = extractActionFromSumType(sumType);
    NondetPicks picks = extractNondetFromSumType(sumType);
    ItfValue.Record rest = state.without(MBT_ACTION_TAKEN).without(MBT_NONDET_PICKS);
    return new Step(action, picks, extractValueInPath(rest, statePath));
  }

  static String extractActionFromMbtVar(ItfValue.Record state) {
    ItfValue value =
        state
            .get(MBT_ACTION_TAKEN)
            .orElseThrow(
                () -> new DriverException("Missing `mbt::actionTaken` variable in the trace"));
    if (value instanceof ItfValue.Str(String action)) {
      return action;
    }
    throw new DriverException("Failed to decode `mbt::actionTaken` variable");
  }

  static NondetPicks extractNondetFromMbtVar(ItfValue.Record state) {
    ItfValue value =
        state
            .get(MBT_NONDET_PICKS)
            .orElseThrow(
                () -> new DriverException("Missing `mbt::nondetPicks` variable in the trace"));
    try {
      return NondetPicks.of(value);
    } catch (DriverException e) {
      throw new DriverException("Failed to extract nondet picks from trace", e);
    }
  }

  static ItfValue extractValueInPath(ItfValue.Record state, List<String> path) {
    ItfValue value = state;
    for (String segment : path) {
      if (!(value instanceof ItfValue.Record rec)) {
        throw new DriverException(
            "Can not read "
                + quote(segment)
                + " from non-record value in path: "
                + quote(path)
                + "\nCurrent value: "
                + value.display());
      }
      ItfValue next = rec.get(segment).orElse(null);
      if (next == null) {
        throw new DriverException(
            "Can not find a value at "
                + quote(segment)
                + " in path: "
                + quote(path)
                + "\nCurrent value: "
                + rec.display());
      }
      value = next;
    }
    return value;
  }

  static ItfValue.Record findRecordInPath(ItfValue.Record state, List<String> path) {
    ItfValue.Record rec = state;
    for (String segment : path) {
      if (!(rec.get(segment).orElse(null) instanceof ItfValue.Record next)) {
        throw new DriverException(
            "Can not find a Record at "
                + quote(segment)
                + " in path: "
                + quote(path)
                + "\nCurrent state: "
                + state.display());
      }
      rec = next;
    }
    return rec;
  }

  static String extractActionFromSumType(ItfValue.Record sumType) {
    if (sumType.get("tag").orElse(null) instanceof ItfValue.Str(String action)) {
      return action;
    }
    throw new DriverException(
        "Expected action to be a sum type variant.\nValue found: " + sumType.display());
  }

  static NondetPicks extractNondetFromSumType(ItfValue.Record sumType) {
    ItfValue value = sumType.get("value").orElse(null);
    if (value instanceof ItfValue.Tuple tuple && tuple.isEmpty()) {
      return NondetPicks.empty();
    }
    if (value instanceof ItfValue.Record rec) {
      return NondetPicks.of(rec);
    }
    throw new DriverException(
        "Expected nondet picks to be a sum type variant value as a record.\nValue found: "
            + sumType.display());
  }

  /** Mirrors Rust's {@code {:?}} for a string slice. */
  private static String quote(String s) {
    return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }

  /** Mirrors Rust's {@code {:?}} for a slice of string slices, e.g. {@code ["a", "b"]}. */
  private static String quote(List<String> path) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < path.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(quote(path.get(i)));
    }
    return sb.append(']').toString();
  }
}
