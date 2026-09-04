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

import java.util.Optional;

/** Helpers for interpreting Quint idioms encoded in {@link ItfValue} trees. */
public final class ItfValues {

  private ItfValues() {}

  /**
   * Interprets {@code value} as a Quint {@code Option}.
   *
   * <p>A record tagged {@code Some} yields its {@code value} field, a record tagged {@code None}
   * yields {@link Optional#empty()}, and any other value is returned unchanged inside an {@link
   * Optional}. A {@code Some} record without a {@code value} field also yields empty, mirroring the
   * upstream implementation.
   */
  public static Optional<ItfValue> asOption(ItfValue value) {
    if (value instanceof ItfValue.Record rec
        && rec.get("tag").orElse(null) instanceof ItfValue.Str(String tag)) {
      if (tag.equals("Some")) {
        return rec.get("value");
      }
      if (tag.equals("None")) {
        return Optional.empty();
      }
    }
    return Optional.of(value);
  }
}
