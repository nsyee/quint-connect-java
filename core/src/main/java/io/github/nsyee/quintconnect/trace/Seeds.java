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
package io.github.nsyee.quintconnect.trace;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;

/**
 * Resolution of the random seed passed to Quint.
 *
 * <p>Order: explicit value, {@code QUINT_SEED} system property, {@code QUINT_SEED} environment
 * variable, then a random 32-bit value rendered as {@code 0x…}. Unlike the Rust crate, which reads
 * {@code QUINT_SEED} at compile time, the value is read on every call.
 */
public final class Seeds {

  /** Name of the system property / environment variable holding a fixed seed. */
  public static final String QUINT_SEED = "QUINT_SEED";

  private static final Random RANDOM = new SecureRandom();

  private Seeds() {}

  /** Resolves a seed as described in the class documentation. */
  public static String resolve(Optional<String> explicit) {
    return resolve(
        explicit,
        Optional.ofNullable(System.getProperty(QUINT_SEED)),
        Optional.ofNullable(System.getenv(QUINT_SEED)),
        RANDOM);
  }

  /** Resolves a seed with no explicit value. */
  public static String resolve() {
    return resolve(Optional.empty());
  }

  static String resolve(
      Optional<String> explicit, Optional<String> property, Optional<String> env, Random random) {
    return explicit
        .filter(Seeds::nonBlank)
        .or(() -> property.filter(Seeds::nonBlank))
        .or(() -> env.filter(Seeds::nonBlank))
        .orElseGet(() -> random(random));
  }

  /** Formats a random 32-bit seed as {@code 0x%x}. */
  static String random(Random random) {
    return String.format("0x%x", random.nextInt());
  }

  private static boolean nonBlank(String s) {
    return !s.isBlank();
  }
}
