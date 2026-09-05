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
package io.github.nsyee.quintconnect.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoggerTest {

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

  private String output() {
    return buffer.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
  }

  /** Port of {@code logger/util.rs} {@code test_ident}. */
  @Test
  void indent() {
    assertEquals("foo\nbar", Logger.indent(0, "foo\nbar"));
    assertEquals("  foo\n  bar", Logger.indent(2, "foo\nbar"));
    assertEquals("  foo\n  bar\n  bazz", Logger.indent(2, "foo\nbar\nbazz"));
    assertEquals("  foo\n", Logger.indent(2, "foo\n"));
    assertEquals("", Logger.indent(2, ""));
  }

  @Test
  void plainOutput() {
    Logger logger = new Logger(out, 0, false);
    logger.title("Running model based tests for t");
    logger.info("Generating 1 traces");
    logger.success("[OK] t");
    logger.error("[FAIL] t\nsecond line");
    assertEquals(
        """
        == Running model based tests for t
           Generating 1 traces
           [OK] t
           [FAIL] t
           second line
        """,
        output());
  }

  @Test
  void traceIsGatedByVerbosity() {
    Logger logger = new Logger(out, 1, false);
    logger.trace(1, "shown");
    logger.trace(2, "hidden");
    assertEquals("   shown\n", output());
    assertTrue(logger.isEnabled(1));
    assertFalse(logger.isEnabled(2));
    assertTrue(logger.withVerbosity(2).isEnabled(2));
  }

  @Test
  void colors() {
    Logger logger = new Logger(out, 1, true);
    logger.title("t");
    logger.success("ok");
    logger.error("no");
    logger.trace(1, "tr");
    logger.info("plain");
    String esc = "\u001b";
    List<String> expected =
        List.of(
            esc + "[1m== t" + esc + "[0m",
            esc + "[1m" + esc + "[32m   ok" + esc + "[0m",
            esc + "[1m" + esc + "[31m   no" + esc + "[0m",
            esc + "[2m" + esc + "[97m   tr" + esc + "[0m",
            "   plain");
    assertEquals(String.join("\n", expected) + "\n", output());
  }

  @Test
  void silentPrintsNothing() {
    Logger logger = Logger.silent();
    logger.title("t");
    logger.error("e");
    assertEquals(0, logger.verbosity());
  }

  @Test
  void resolveVerbosity() {
    assertEquals(0, Logger.resolveVerbosity(Optional.empty(), Optional.empty()));
    assertEquals(2, Logger.resolveVerbosity(Optional.empty(), Optional.of("2")));
    assertEquals(1, Logger.resolveVerbosity(Optional.of("1"), Optional.of("2")));
    assertEquals(2, Logger.resolveVerbosity(Optional.of(" "), Optional.of("2")));
    assertEquals(0, Logger.resolveVerbosity(Optional.of("verbose"), Optional.empty()));
    assertEquals(0, Logger.resolveVerbosity(Optional.of("-1"), Optional.empty()));
  }

  @Test
  void colorEnabled() {
    Optional<String> set = Optional.of("1");
    Optional<String> unset = Optional.empty();
    assertFalse(Logger.colorEnabled(set, set, Optional.of("xterm")));
    assertTrue(Logger.colorEnabled(unset, set, unset));
    assertTrue(Logger.colorEnabled(unset, unset, Optional.of("xterm-256color")));
    assertFalse(Logger.colorEnabled(unset, unset, Optional.of("dumb")));
    assertFalse(Logger.colorEnabled(unset, unset, unset));
  }
}
