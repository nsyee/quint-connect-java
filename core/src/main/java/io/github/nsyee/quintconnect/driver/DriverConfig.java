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

import java.util.List;

/**
 * Tells the framework where to find the specification state and the nondeterministic picks inside
 * an ITF state record.
 *
 * <p>Both paths are empty by default, meaning that
 *
 * <ul>
 *   <li>the state is the whole record (minus the {@code mbt::*} variables), and
 *   <li>the action and the picks come from Quint's builtin {@code mbt::actionTaken} and {@code
 *       mbt::nondetPicks} variables.
 * </ul>
 *
 * <p>Set {@code statePath} when the specification nests the state of interest inside a larger
 * structure, and {@code nondetPath} when the specification tracks the action itself as a sum type
 * (for instance Choreo's {@code extensions.actionTaken}) instead of relying on {@code --mbt}:
 *
 * <pre>{@code
 * DriverConfig.statePath("two_phase_commit::choreo::s", "system")
 *     .nondetPath("two_phase_commit::choreo::s", "extensions", "actionTaken");
 * }</pre>
 *
 * @param statePath field names leading to the specification state
 * @param nondetPath field names leading to the sum-type value describing the action taken
 */
public record DriverConfig(List<String> statePath, List<String> nondetPath) {

  /** Top-level state, {@code mbt::*} variables. */
  public static final DriverConfig DEFAULT = new DriverConfig(List.of(), List.of());

  /** Creates a configuration, taking immutable copies of both paths. */
  public DriverConfig {
    statePath = List.copyOf(statePath);
    nondetPath = List.copyOf(nondetPath);
  }

  /** Returns a configuration with the given state path and the default nondet path. */
  public static DriverConfig statePath(String... segments) {
    return new DriverConfig(List.of(segments), List.of());
  }

  /** Returns a copy of this configuration with the given state path. */
  public DriverConfig withStatePath(String... segments) {
    return new DriverConfig(List.of(segments), nondetPath);
  }

  /** Returns a copy of this configuration with the given nondet (sum type) path. */
  public DriverConfig nondetPath(String... segments) {
    return new DriverConfig(statePath, List.of(segments));
  }

  /** Whether the action and picks come from the {@code mbt::*} variables. */
  public boolean usesMbtVars() {
    return nondetPath.isEmpty();
  }
}
