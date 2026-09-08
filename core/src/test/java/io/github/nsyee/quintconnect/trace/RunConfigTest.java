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

import io.github.nsyee.quintconnect.parity.Parity;
import java.nio.file.Path;
import java.util.List;
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

  /** The full command line documented in design.md §5.4: every flag, in this exact order. */
  @Test
  void allFlagsInDocumentedOrder() {
    RunConfig config =
        RunConfig.of(Path.of("spec/tictactoe.qnt"), "0x2a")
            .withMaxSamples(7)
            .withMain("simulation")
            .withInit("my_init")
            .withStep("my_step")
            .withMaxSteps(32);
    assertEquals(
        List.of(
            "run",
            "spec/tictactoe.qnt",
            "--seed",
            "0x2a",
            "--max-samples",
            "7",
            "--n-traces",
            "7",
            "--out-itf",
            "tmpdir/run_{seq}.itf.json",
            "--mbt",
            "--verbosity",
            "0",
            "--main",
            "simulation",
            "--init",
            "my_init",
            "--step",
            "my_step",
            "--max-steps",
            "32"),
        config.toCommand(Path.of("tmpdir")).stream().map(s -> s.replace('\\', '/')).toList());
  }

  /**
   * The command line quint-connect v0.1.2 really spawned for the golden run (captured by {@code
   * parity/bin/quint}), compared argument by argument.
   */
  @Test
  void matchesCommandSpawnedByRust() {
    Path tmp = Path.of("tmp");
    RunConfig config =
        RunConfig.of(Path.of(Parity.HARNESS_SPEC), Parity.GOLDEN_SEED).withMaxSamples(1);
    String captured = Parity.goldenText(Parity.GOLDEN_COMMAND).strip();
    assertEquals(captured, Parity.normalizeCommand(QuintCli.of("quint").command(config, tmp), tmp));
  }

  @Test
  void nTracesDefaultsTo100() {
    assertEquals(100, basicConfig().nTraces());
    assertEquals(7, basicConfig().withMaxSamples(7).nTraces());
  }
}
