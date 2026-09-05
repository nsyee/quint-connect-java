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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfMeta;
import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfState;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.itf.ItfValue;
import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.runner.TicTacToeDrivers.Correct;
import io.github.nsyee.quintconnect.runner.TicTacToeDrivers.Diverging;
import io.github.nsyee.quintconnect.runner.TicTacToeDrivers.Stateless;
import io.github.nsyee.quintconnect.runner.TicTacToeDrivers.Throwing;
import io.github.nsyee.quintconnect.trace.FileTraceGenerator;
import io.github.nsyee.quintconnect.trace.RunConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class QuintConnectTest {

  private static final RunConfig CONFIG =
      RunConfig.of(Path.of("spec/tictactoe.qnt"), "0x2a").withMaxSamples(1);

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final Logger logger =
      new Logger(new PrintStream(buffer, true, StandardCharsets.UTF_8), 1, false);

  private String log() {
    return buffer.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
  }

  private static Path fixture(String name) {
    try {
      return Path.of(QuintConnectTest.class.getResource("/itf/" + name + ".itf.json").toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private QuintConnect runner(Path... files) {
    return QuintConnect.using(FileTraceGenerator.of(files)).withLogger(logger);
  }

  @Test
  void correctDriverPasses() {
    Correct driver = new Correct();
    runner(fixture("tictactoe")).runTest("tictactoe", () -> driver, CONFIG);

    assertEquals(List.of("init", "MoveX", "MoveO", "MoveX", "MoveO", "MoveX"), driver.actions);
    String log = log();
    assertTrue(log.startsWith("== Running model based tests for tictactoe\n"), log);
    assertTrue(log.contains("   Generating 1 traces using `0x2a` as random seed ...\n"), log);
    assertTrue(log.contains("   Replaying traces ...\n"), log);
    assertTrue(log.contains("   [Trace 1]\n"), log);
    assertTrue(log.contains("   [Step 0]\n   Action taken: init\n"), log);
    assertTrue(log.endsWith("   [OK] tictactoe\n"), log);
    assertFalse(log.contains("[FAIL]"), log);
  }

  @Test
  void divergingDriverFailsWithDiff() {
    QuintConnectException e =
        assertThrows(
            QuintConnectException.class,
            () -> runner(fixture("tictactoe")).runTest("tictactoe", Diverging::new, CONFIG));

    assertEquals(QuintConnect.STATE_INVARIANT_FAILED, e.summary());
    assertEquals(OptionalInt.of(1), e.traceIndex());
    assertEquals(OptionalInt.of(2), e.stepIndex());
    assertEquals(Optional.of("MoveO"), e.action());
    assertTrue(e.diff().isPresent());
    String expectedDiff =
        """
        --- specification
        +++ implementation
        @@ -1,24 +1,24 @@
         {
        -  nextTurn: X,
        +  nextTurn: O,
           board: Map(
        """;
    assertTrue(e.diff().get().unified().startsWith(expectedDiff), e.getMessage());
    assertTrue(
        e.getMessage()
            .startsWith(
                "State invariant failed (trace 1, step 2, action `MoveO`)\n" + expectedDiff),
        e.getMessage());

    String log = log();
    assertTrue(log.contains("   Specification and implementation states diverge\n"), log);
    assertTrue(log.contains("   --- specification\n   +++ implementation\n"), log);
    assertTrue(log.contains("   -  nextTurn: X,\n   +  nextTurn: O,\n"), log);
    assertTrue(log.contains("   [FAIL] tictactoe \n"), log);
    assertTrue(log.contains("   Reproduce this error with `QUINT_SEED=0x2a`\n"), log);
  }

  @Test
  void driverExceptionPropagatesWithPosition() {
    QuintConnectException e =
        assertThrows(
            QuintConnectException.class,
            () -> runner(fixture("tictactoe")).runTest("tictactoe", Throwing::new, CONFIG));

    assertEquals("boom", e.summary());
    assertEquals("boom (trace 1, step 2, action `MoveO`)", e.getMessage());
    assertInstanceOf(java.io.IOException.class, e.getCause());
    assertTrue(e.diff().isEmpty());
    assertTrue(log().contains("[FAIL] tictactoe"), log());
  }

  @Test
  void voidStateSkipsChecking() {
    Stateless driver = new Stateless();
    runner(fixture("tictactoe"), fixture("tictactoe")).runTest("stateless", () -> driver, CONFIG);
    assertEquals(12, driver.actions.size());
    assertTrue(log().contains("   [Trace 2]\n"), log());
  }

  @Test
  void zeroTracesFail() {
    QuintConnectException e =
        assertThrows(
            QuintConnectException.class, () -> runner().runTest("empty", Correct::new, CONFIG));
    assertEquals(QuintConnect.ZERO_TRACES, e.getMessage());
    assertEquals(
        "Trace generation produced zero traces.\n"
            + "Please check your specification and/or your test configuration.",
        e.getMessage());
    assertTrue(log().contains("[FAIL] empty"), log());
  }

  @Test
  void anonymousActionIsRejected() {
    ItfTrace original = ItfParser.parse(fixture("tictactoe"));
    ItfState first = original.states().get(0);
    ItfValue.Record anonymous =
        first.value().without("mbt::actionTaken").without("mbt::nondetPicks");
    var fields = new java.util.LinkedHashMap<>(anonymous.fields());
    fields.put("mbt::actionTaken", new ItfValue.Str(""));
    fields.put("mbt::nondetPicks", first.value().get("mbt::nondetPicks").orElseThrow());
    ItfTrace trace =
        new ItfTrace(
            ItfMeta.EMPTY,
            original.vars(),
            List.of(new ItfState(0, new ItfValue.Record(fields))),
            OptionalInt.empty());

    QuintConnectException e =
        assertThrows(
            QuintConnectException.class, () -> runner().replayTrace(new Correct(), trace, 3));
    assertEquals(QuintConnect.ANONYMOUS_ACTION, e.summary());
    assertTrue(e.getMessage().endsWith("(trace 3, step 0)"), e.getMessage());
    assertTrue(e.action().isEmpty());
  }

  @Test
  void stepExtractionErrorsCarryPosition() {
    ItfTrace trace =
        new ItfTrace(
            ItfMeta.EMPTY,
            List.of("x"),
            List.of(new ItfState(0, ItfValue.Record.of("x", ItfValue.Int.of(1)))),
            OptionalInt.empty());
    QuintConnectException e =
        assertThrows(
            QuintConnectException.class, () -> runner().replayTrace(new Correct(), trace, 1));
    assertEquals(
        "Missing `mbt::actionTaken` variable in the trace (trace 1, step 0)", e.getMessage());
  }

  @Test
  void tracesStreamYieldsOneRunPerTrace() {
    Correct driver = new Correct();
    try (Stream<TraceRun> runs =
        runner(fixture("tictactoe"), fixture("two_phase_commit"))
            .traces(() -> driver, CONFIG.withMaxSamples(2))) {
      List<TraceRun> list = runs.toList();
      assertEquals(2, list.size());
      assertEquals("[Trace 1/2] seed=0x2a", list.get(0).displayName());
      assertEquals("[Trace 2/2] seed=0x2a", list.get(1).displayName());
      assertEquals(6, list.get(0).trace().length());

      list.get(0).replay();
      assertEquals(6, driver.actions.size());

      QuintConnectException e = assertThrows(QuintConnectException.class, list.get(1)::replay);
      assertEquals(OptionalInt.of(2), e.traceIndex());
    }
  }

  @Test
  void tracesStreamRejectsZeroTraces() {
    assertThrows(QuintConnectException.class, () -> runner().traces(Correct::new, CONFIG));
  }

  @Test
  void accessors() {
    QuintConnect qc = runner();
    assertSame(logger, qc.logger());
    assertSame(qc.mapper(), QuintConnect.create().mapper());
  }
}
