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

import java.util.Objects;

/**
 * One state of an {@link ItfTrace}.
 *
 * @param index the state index from {@code #meta.index} (or the position in the trace when the tool
 *     did not emit one)
 * @param value the state variables as a record, without the {@code #meta} entry
 */
public record ItfState(int index, ItfValue.Record value) {

  /** Creates a state, rejecting a {@code null} value. */
  public ItfState {
    Objects.requireNonNull(value, "value");
  }
}
