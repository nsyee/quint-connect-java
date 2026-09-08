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
import java.util.List;
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

  /** The full command line documented in design.md §5.4: every flag, in this exact order. */
  @Test
  void allFlagsInDocumentedOrder() {
    TestConfig config =
        TestConfig.of(Path.of("spec/two_phase_commit.qnt"), "commitTest", "0x2a")
            .withMaxSamples(7)
            .withMain("tests");
    assertEquals(
        List.of(
            "test",
            "spec/two_phase_commit.qnt",
            "--seed",
            "0x2a",
            "--match",
            "^commitTest$",
            "--max-samples",
            "7",
            "--out-itf",
            "tmpdir/test_{seq}.itf.json",
            "--verbosity",
            "0",
            "--main",
            "tests"),
        config.toCommand(Path.of("tmpdir")).stream().map(s -> s.replace('\\', '/')).toList());
  }

  /** {@code quint test} never passes {@code --mbt} or {@code --n-traces}. */
  @Test
  void noSimulationOnlyFlags() {
    List<String> cmd = basicConfig().toCommand(Path.of("tmpdir"));
    assertEquals(
        List.of(), cmd.stream().filter(a -> a.equals("--mbt") || a.equals("--n-traces")).toList());
  }

  @Test
  void maxSamples() {
    assertEquals(
        "quint test foo.qnt --seed 42 --match ^happyTest$ --max-samples 42"
            + " --out-itf tmpdir/test_{seq}.itf.json --verbosity 0",
        toString(basicConfig().withMaxSamples(42)));
  }
}
