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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.parity.Parity;
import io.github.nsyee.quintconnect.trace.FileTraceGenerator;
import io.github.nsyee.quintconnect.trace.RunConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * Replays the trace that quint-connect v0.1.2 (Rust) generated with {@code QUINT_SEED=0x2a} and
 * checks that this port produces the same result and the same log as the Rust run captured in the
 * golden fixtures. No CLI is needed; see {@code docs/parity.md}.
 */
class RustParityTest {

  /**
   * The Rust reference driver ({@code parity/rust-harness/src/lib.rs}) ignores the {@code
   * stuttered} steps Quint pads a finished game with; so do these.
   */
  static class Correct extends TicTacToeDrivers.Correct {
    @Override
    public void step(Step step) throws Exception {
      if (step.action().equals("stuttered")) {
        actions.add(step.action());
        return;
      }
      super.step(step);
    }
  }

  /** Same divergence as {@code Diverging} in the Rust harness. */
  static final class Diverging extends Correct {
    @Override
    public void step(Step step) throws Exception {
      super.step(step);
      if (step.action().equals("MoveO")) {
        game.nextTurn = TicTacToeDrivers.Player.O;
      }
    }
  }

  private static final Path SPEC = Path.of(Parity.HARNESS_SPEC);
  private static final RunConfig CONFIG = RunConfig.of(SPEC, Parity.GOLDEN_SEED).withMaxSamples(1);

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final Logger logger =
      new Logger(new PrintStream(buffer, true, StandardCharsets.UTF_8), 1, false);

  private String log() {
    return Parity.normalizeLog(buffer.toString(StandardCharsets.UTF_8));
  }

  private QuintConnect runner() {
    return QuintConnect.using(FileTraceGenerator.of(Parity.golden(Parity.GOLDEN_TRACE)))
        .withLogger(logger);
  }

  @Test
  void goldenTraceParses() {
    ItfTrace trace = ItfParser.parse(Parity.golden(Parity.GOLDEN_TRACE));
    assertTrue(trace.vars().containsAll(List.of("nextTurn", "board", "mbt::actionTaken")));
    assertEquals(21, trace.length());
  }

  @Test
  void correctDriverPassesWithIdenticalLog() {
    Correct driver = new Correct();
    runner().runTest("tictactoe", () -> driver, CONFIG);

    assertEquals(
        List.of("init", "MoveX", "MoveO", "MoveX", "MoveO", "MoveX"), driver.actions.subList(0, 6));
    assertEquals(21, driver.actions.size());
    assertEquals(Parity.normalizeLog(Parity.goldenText(Parity.GOLDEN_OK_LOG)), log());
  }

  @Test
  void divergingDriverFailsLikeRust() {
    QuintConnectException e =
        assertThrows(
            QuintConnectException.class,
            () -> runner().runTest("tictactoe_diverging", Diverging::new, CONFIG));

    // `bail!("State invariant failed")` in check_state.
    assertEquals(QuintConnect.STATE_INVARIANT_FAILED, e.summary());
    assertEquals(OptionalInt.of(1), e.traceIndex());
    assertEquals(OptionalInt.of(2), e.stepIndex());
    assertEquals(Optional.of("MoveO"), e.action());
    assertEquals(
        List.of("--- specification", "+++ implementation", "@@", "- nextTurn: X", "+ nextTurn: O"),
        Parity.diffEssence(e.diff().orElseThrow().unified()));

    String rust = Parity.goldenText(Parity.GOLDEN_DIVERGING_LOG);
    Parity.assertLogsEquivalent(rust, log());

    // The essence of the Rust diff is the same, even though it renders `GameState { … }`.
    assertEquals(
        List.of("--- specification", "+++ implementation", "@@", "- nextTurn: X", "+ nextTurn: O"),
        Parity.diffEssence(Parity.split(rust).diff()));

    String log = log();
    assertTrue(log.contains("   Specification and implementation states diverge\n"), log);
    assertTrue(log.contains("   [FAIL] tictactoe_diverging \n"), log);
    assertTrue(
        log.endsWith(
            "   [FAIL] tictactoe_diverging \n   Reproduce this error with `QUINT_SEED=0x2a`\n"),
        log);
  }
}
