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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Port of the unit tests in {@code trace/generator/test.rs}. */
class TestConfigTest {

  private static TestConfig basicConfig() {
    return TestConfig.of(Path.of("foo.qnt"), "happyTest", "42");
  }

  private static String toString(GenConfig config) {
    return "quint " + String.join(" ", config.toCommand(Path.of("tmpdir"))).replace('\\', '/');
  }

  @Test
  void basic() {
    assertEquals(
        "quint test foo.qnt --seed 42 --match ^happyTest$ --max-samples 100"
            + " --out-itf tmpdir/test_{seq}.itf.json --verbosity 0",
        toString(basicConfig()));
  }

  @Test
  void mainModule() {
    assertEquals(
        "quint test foo.qnt --seed 42 --match ^happyTest$ --max-samples 100"
            + " --out-itf tmpdir/test_{seq}.itf.json --verbosity 0 --main tests",
        toString(basicConfig().withMain("tests")));
  }

  @Test
  void maxSamples() {
    assertEquals(
        "quint test foo.qnt --seed 42 --match ^happyTest$ --max-samples 42"
            + " --out-itf tmpdir/test_{seq}.itf.json --verbosity 0",
        toString(basicConfig().withMaxSamples(42)));
  }
}
