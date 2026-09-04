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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The {@code #meta} object of an ITF trace. Only string-valued fields and the well-known {@code
 * timestamp} / {@code varTypes} entries are interpreted; everything else is dropped.
 *
 * @param format the {@code format} field, normally {@code "ITF"}
 * @param formatDescription the {@code format-description} field
 * @param source the specification the trace was generated from
 * @param status the tool status, e.g. {@code "ok"}, {@code "passed"} or {@code "violation"}
 * @param description free-form description
 * @param timestamp creation time in milliseconds since the epoch
 * @param varTypes Apalache type annotations per state variable (possibly empty)
 */
public record ItfMeta(
    Optional<String> format,
    Optional<String> formatDescription,
    Optional<String> source,
    Optional<String> status,
    Optional<String> description,
    OptionalLong timestamp,
    Map<String, String> varTypes) {

  /** A metadata object with no fields set. */
  public static final ItfMeta EMPTY =
      new ItfMeta(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Map.of());

  /** Creates a metadata object, taking an immutable copy of {@code varTypes}. */
  public ItfMeta {
    varTypes = Collections.unmodifiableMap(new LinkedHashMap<>(varTypes));
  }
}
