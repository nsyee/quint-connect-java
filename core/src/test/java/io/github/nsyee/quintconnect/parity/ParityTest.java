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
package io.github.nsyee.quintconnect.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/** Tests of the normalisation helpers themselves. */
class ParityTest {

  @Test
  void stripsAnsiAndNewlines() {
    assertEquals(
        "== title\n   [OK] x\n",
        Parity.normalizeLog(
            "\u001B[1m== title\u001B[0m\r\n\u001B[1m\u001B[32m   [OK] x\u001B[0m\r\n"));
  }

  @Test
  void dropsRustPanicTrailer() {
    String rust =
        """
        == Running model based tests for t
           [FAIL] t\s
           Reproduce this error with `QUINT_SEED=0x2a`


        thread 't' (8287) panicked at tests/tictactoe.rs:28:1:
        State invariant failed
        note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace
        """;
    assertEquals(
        """
        == Running model based tests for t
           [FAIL] t\s
           Reproduce this error with `QUINT_SEED=0x2a`
        """,
        Parity.normalizeLog(rust));
  }

  @Test
  void normalizesCommand() {
    Path tmp = Path.of("/tmp/quint-connect-abc");
    assertEquals(
        "quint run foo.qnt --out-itf <tmp>/run_{seq}.itf.json",
        Parity.normalizeCommand(
            List.of(
                "quint",
                "run",
                "foo.qnt",
                "--out-itf",
                tmp.resolve("run_{seq}.itf.json").toString()),
            tmp));
  }

  @Test
  void diffEssenceIgnoresRenderingDetails() {
    String rust =
        """
           --- specification
           +++ implementation
           @@ -1,24 +1,24 @@
            GameState {
                board: {
                    1: Empty,
                },
           -    next_turn: X,
           +    next_turn: O,
            }
        """;
    String java =
        """
        --- specification
        +++ implementation
        @@ -1,3 +1,3 @@
         {
        -  nextTurn: X,
        +  nextTurn: O,
           board: Map(1 -> Empty),
         }""";
    List<String> expected =
        List.of("--- specification", "+++ implementation", "@@", "- nextTurn: X", "+ nextTurn: O");
    assertEquals(expected, Parity.diffEssence(rust));
    assertEquals(expected, Parity.diffEssence(java));
  }

  @Test
  void splitIsolatesTheDiff() {
    String log =
        """
        == Running model based tests for t
           Specification and implementation states diverge
           --- specification
           +++ implementation
           @@ -1 +1 @@
           -a
           +b

           [FAIL] t\s
           Reproduce this error with `QUINT_SEED=0x2a`
        """;
    Parity.LogParts parts = Parity.split(log);
    assertEquals(
        "== Running model based tests for t\n   Specification and implementation states diverge\n",
        parts.before());
    assertEquals(
        "   --- specification\n   +++ implementation\n   @@ -1 +1 @@\n   -a\n   +b\n",
        parts.diff());
    assertEquals("\n   [FAIL] t \n   Reproduce this error with `QUINT_SEED=0x2a`\n", parts.after());
    Parity.assertLogsEquivalent(log, log);
    assertThrows(
        AssertionFailedError.class,
        () -> Parity.assertLogsEquivalent(log, log.replace("+b", "+c")));
    assertThrows(
        AssertionFailedError.class,
        () -> Parity.assertLogsEquivalent(log, log.replace("[FAIL]", "[OK]")));
  }

  @Test
  void logsWithoutDiffCompareVerbatim() {
    Parity.LogParts parts = Parity.split("== a\n   [OK] a\n");
    assertEquals("== a\n   [OK] a\n", parts.before());
    assertEquals("", parts.diff());
    assertEquals("", parts.after());
  }
}
