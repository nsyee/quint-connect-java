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
package io.github.nsyee.quintconnect.examples.counter;

/**
 * A bounded counter: the "system under test" of the TLA+/Apalache example, written without any
 * knowledge of the specification.
 */
public final class Counter {

  private long count;

  /** The current value, never negative. */
  public long count() {
    return count;
  }

  /** Adds {@code n} (positive). */
  public void increment(long n) {
    if (n <= 0) {
      throw new IllegalArgumentException("n must be positive");
    }
    count += n;
  }

  /** Subtracts {@code n} (positive); fails if the counter would become negative. */
  public void decrement(long n) {
    if (n <= 0) {
      throw new IllegalArgumentException("n must be positive");
    }
    if (count < n) {
      throw new IllegalStateException("counter would become negative");
    }
    count -= n;
  }

  /** Sets the counter back to zero. */
  public void reset() {
    count = 0;
  }
}
