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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SeedsTest {

  private static final Random FIXED = new Random(0);

  @Test
  void explicitWins() {
    assertEquals("7", Seeds.resolve(Optional.of("7"), Optional.of("8"), Optional.of("9"), FIXED));
  }

  @Test
  void propertyBeforeEnv() {
    assertEquals("8", Seeds.resolve(Optional.empty(), Optional.of("8"), Optional.of("9"), FIXED));
    assertEquals("9", Seeds.resolve(Optional.empty(), Optional.empty(), Optional.of("9"), FIXED));
  }

  @Test
  void blankValuesAreIgnored() {
    assertEquals("9", Seeds.resolve(Optional.of(" "), Optional.of(""), Optional.of("9"), FIXED));
  }

  @Test
  void randomSeedIsHex32() {
    String seed = Seeds.resolve(Optional.empty(), Optional.empty(), Optional.empty(), FIXED);
    assertTrue(seed.matches("0x[0-9a-f]{1,8}"), seed);
    assertEquals(
        "0xffffffff",
        Seeds.random(
            new Random() {
              private static final long serialVersionUID = 1L;

              @Override
              public int nextInt() {
                return -1;
              }
            }));
  }
}
