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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nsyee.quintconnect.itf.ItfValue.Bool;
import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Port of the {@code value/option.rs} unit tests. */
class ItfValuesTest {

  @Test
  void someWithValue() {
    ItfValue some = Record.of("tag", new Str("Some"), "value", Int.of(42));
    assertEquals(Optional.of(Int.of(42)), ItfValues.asOption(some));
  }

  @Test
  void none() {
    ItfValue none = Record.of("tag", new Str("None"));
    assertEquals(Optional.empty(), ItfValues.asOption(none));
    ItfValue noneWithUnit = Record.of("tag", new Str("None"), "value", Tuple.of());
    assertEquals(Optional.empty(), ItfValues.asOption(noneWithUnit));
  }

  @Test
  void nonOptionRecord() {
    ItfValue rec = Record.of("foo", Int.of(42), "bar", Bool.TRUE);
    assertEquals(Optional.of(rec), ItfValues.asOption(rec));
  }

  @Test
  void recordWithNonStringTag() {
    ItfValue rec = Record.of("tag", Int.of(42), "value", new Str("test"));
    assertEquals(Optional.of(rec), ItfValues.asOption(rec));
  }

  @Test
  void otherVariantsAreNotUnwrapped() {
    ItfValue rec = Record.of("tag", new Str("Foo"), "value", Int.of(1));
    assertEquals(Optional.of(rec), ItfValues.asOption(rec));
  }

  @Test
  void scalarsPassThrough() {
    assertEquals(Optional.of(Int.of(1)), ItfValues.asOption(Int.of(1)));
    assertEquals(Optional.of(Bool.FALSE), ItfValues.asOption(Bool.FALSE));
  }
}
