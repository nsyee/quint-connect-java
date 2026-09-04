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
package io.github.nsyee.quintconnect.itf;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * One ITF trace: metadata, the declared state variables and the sequence of states.
 *
 * @param meta the {@code #meta} object
 * @param vars the declared state variable names ({@code vars})
 * @param states the states, in trace order
 * @param loop index of the state the trace loops back to (lasso traces), if any
 */
public record ItfTrace(ItfMeta meta, List<String> vars, List<ItfState> states, OptionalInt loop) {

  /** Creates a trace, taking immutable copies of the collections. */
  public ItfTrace {
    Objects.requireNonNull(meta, "meta");
    Objects.requireNonNull(loop, "loop");
    vars = List.copyOf(vars);
    states = List.copyOf(states);
  }

  /** Returns the number of states. */
  public int length() {
    return states.size();
  }
}
