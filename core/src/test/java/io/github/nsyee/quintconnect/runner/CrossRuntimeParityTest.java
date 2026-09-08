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
package io.github.nsyee.quintconnect.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.parity.Parity;
import io.github.nsyee.quintconnect.trace.FileTraceGenerator;
import io.github.nsyee.quintconnect.trace.QuintCli;
import io.github.nsyee.quintconnect.trace.QuintTraceGenerator;
import io.github.nsyee.quintconnect.trace.RunConfig;
import io.github.nsyee.quintconnect.trace.TraceSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs quint-connect v0.1.2 (Rust, via {@code parity/run-rust-harness.sh}) and this port on the
 * same specification with the same {@code QUINT_SEED}, and compares the command line spawned, the
 * ITF {@code states} generated and the normalised stderr logs.
 *
 * <p>Needs {@code quint} and {@code cargo} on {@code PATH}; runs only in the {@code parityTest}
 * Gradle task ({@code @Tag("parity")}). The seeds are deliberately not limited to the committed
 * golden seed so that parity is not just replaying the fixtures.
 */
@Tag("parity")
class CrossRuntimeParityTest {

  private static final Path PARITY_DIR =
      Path.of(System.getProperty("quintconnect.parity.dir", "../parity")).toAbsolutePath();
  private static final Path SPEC = Path.of("src/test/resources/spec/tictactoe.qnt");

  /** What one Rust run left behind. */
  record RustRun(Path trace, String log, String command, boolean passed) {}

  private static RustRun runRust(String testName, Path out, String seed)
      throws IOException, InterruptedException {
    Process p =
        new ProcessBuilder(
                PARITY_DIR.resolve("run-rust-harness.sh").toString(),
                testName,
                out.toString(),
                seed,
                "1")
            .directory(PARITY_DIR.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(p.waitFor(10, TimeUnit.MINUTES), "rust harness timed out\n" + output);
    assertEquals(0, p.exitValue(), "rust harness failed\n" + output);

    List<Path> traces;
    try (var files = Files.list(out)) {
      traces =
          files.filter(f -> f.getFileName().toString().endsWith(".itf.json")).sorted().toList();
    }
    assertEquals(1, traces.size(), "one trace expected, got " + traces);
    return new RustRun(
        traces.get(0),
        Parity.read(out.resolve("stderr.txt")),
        Parity.read(out.resolve("commands.txt")).strip(),
        Parity.read(out.resolve("status.txt")).strip().equals("0"));
  }

  private static ItfTrace generateWithJava(String seed) {
    RunConfig config = RunConfig.of(SPEC, seed).withMaxSamples(1);
    try (TraceSource source = QuintTraceGenerator.create().generate(config)) {
      List<ItfTrace> traces = source.toList();
      assertEquals(1, traces.size());
      return traces.get(0);
    }
  }

  private static String replayWithJava(
      String testName, Path trace, String seed, boolean diverging) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Logger logger = new Logger(new PrintStream(buffer, true, StandardCharsets.UTF_8), 1, false);
    QuintConnect runner = QuintConnect.using(FileTraceGenerator.of(trace)).withLogger(logger);
    RunConfig config = RunConfig.of(Path.of(Parity.HARNESS_SPEC), seed).withMaxSamples(1);
    if (diverging) {
      QuintConnectException e =
          assertThrows(
              QuintConnectException.class,
              () -> runner.runTest(testName, RustParityTest.Diverging::new, config));
      assertEquals(QuintConnect.STATE_INVARIANT_FAILED, e.summary());
    } else {
      runner.runTest(testName, RustParityTest.Correct::new, config);
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }

  @ParameterizedTest(name = "seed {0}")
  @ValueSource(strings = {"0x2a", "0x1234", "0xdeadbeef"})
  void sameSeedSameTraceAndSameLog(String seed, @TempDir Path tmp) throws Exception {
    RustRun ok = runRust("tictactoe", tmp.resolve("rust-ok"), seed);
    RustRun diverging = runRust("tictactoe_diverging", tmp.resolve("rust-diverging"), seed);
    assertTrue(ok.passed(), ok.log());
    assertFalse(diverging.passed(), diverging.log());
    ItfTrace rustTrace = ItfParser.parse(ok.trace());
    Parity.assertSameStates(rustTrace, ItfParser.parse(diverging.trace()));

    // Layer 3: the same command line is spawned and Quint yields the same states.
    RunConfig config = RunConfig.of(Path.of(Parity.HARNESS_SPEC), seed).withMaxSamples(1);
    Path t = Path.of("t");
    assertEquals(ok.command(), Parity.normalizeCommand(QuintCli.of("quint").command(config, t), t));
    Parity.assertSameStates(rustTrace, generateWithJava(seed));

    // Layer 4: replaying that trace with equivalent drivers gives the Rust log.
    Parity.assertLogsEquivalent(ok.log(), replayWithJava("tictactoe", ok.trace(), seed, false));
    Parity.assertLogsEquivalent(
        diverging.log(), replayWithJava("tictactoe_diverging", ok.trace(), seed, true));
  }
}
