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

/** Port of the unit tests in {@code trace/generator/run.rs}. */
class RunConfigTest {

  private static RunConfig basicConfig() {
    return RunConfig.of(Path.of("foo.qnt"), "42");
  }

  private static String toString(GenConfig config) {
    return "quint " + String.join(" ", config.toCommand(Path.of("tmpdir"))).replace('\\', '/');
  }

  @Test
  void basic() {
    assertEquals(
        "quint run foo.qnt --seed 42 --max-samples 100 --n-traces 100"
            + " --out-itf tmpdir/run_{seq}.itf.json --mbt --verbosity 0",
        toString(basicConfig()));
  }

  @Test
  void mainModule() {
    assertEquals(
        "quint run foo.qnt --seed 42 --max-samples 100 --n-traces 100"
            + " --out-itf tmpdir/run_{seq}.itf.json --mbt --verbosity 0 --main simulation",
        toString(basicConfig().withMain("simulation")));
  }

  @Test
  void initAction() {
    assertEquals(
        "quint run foo.qnt --seed 42 --max-samples 100 --n-traces 100"
            + " --out-itf tmpdir/run_{seq}.itf.json --mbt --verbosity 0 --init my_init",
        toString(basicConfig().withInit("my_init")));
  }

  @Test
  void stepAction() {
    assertEquals(
        "quint run foo.qnt --seed 42 --max-samples 100 --n-traces 100"
            + " --out-itf tmpdir/run_{seq}.itf.json --mbt --verbosity 0 --step my_step",
        toString(basicConfig().withStep("my_step")));
  }

  @Test
  void maxSamples() {
    assertEquals(
        "quint run foo.qnt --seed 42 --max-samples 42 --n-traces 42"
            + " --out-itf tmpdir/run_{seq}.itf.json --mbt --verbosity 0",
        toString(basicConfig().withMaxSamples(42)));
  }

  @Test
  void maxSteps() {
    assertEquals(
        "quint run foo.qnt --seed 42 --max-samples 100 --n-traces 100"
            + " --out-itf tmpdir/run_{seq}.itf.json --mbt --verbosity 0 --max-steps 32",
        toString(basicConfig().withMaxSteps(32)));
  }

  @Test
  void nTracesDefaultsTo100() {
    assertEquals(100, basicConfig().nTraces());
    assertEquals(7, basicConfig().withMaxSamples(7).nTraces());
  }
}
