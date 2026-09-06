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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Tests the process handling with a shell script standing in for {@code apalache-mc}. */
@DisabledOnOs(OS.WINDOWS)
class ApalacheTraceGeneratorTest {

  private static final Path FIXTURE = FileTraceGeneratorTest.fixture("apalache/counter.itf.json");
  private static final ApalacheConfig CONFIG =
      ApalacheConfig.check(Path.of("Counter.tla"), "1").withInvariants("BelowThree");

  /** Writes a script that copies the fixture to the run dir like Apalache would, then exits. */
  private static ApalacheCli fake(Path dir, int exitCode) throws Exception {
    Path script = dir.resolve("fake-apalache");
    String body =
        """
        #!/bin/sh
        for arg in "$@"; do
          case "$arg" in
            --run-dir=*) run="${arg#--run-dir=}" ;;
          esac
        done
        mkdir -p "$run"
        cp "%s" "$run/violation.itf.json"
        cp "%s" "$run/violation1.itf.json"
        cp "%s" "$run/violation2.itf.json"
        cp "%s" "$run/MCCounter.tla"
        echo "fake apalache: $*"
        exit %d
        """
            .formatted(FIXTURE, FIXTURE, FIXTURE, FIXTURE, exitCode);
    Files.writeString(script, body);
    assertTrue(script.toFile().setExecutable(true));
    return ApalacheCli.of(script.toString());
  }

  @Test
  void keepsOnlyNumberedTracesAndCleansUp(@TempDir Path dir) throws Exception {
    Path tempDir;
    try (TraceSource source = ApalacheTraceGenerator.of(fake(dir, 0)).generate(CONFIG)) {
      assertEquals(2, source.size());
      tempDir = source.files().get(0).getParent();
      assertEquals(
          List.of("violation1.itf.json", "violation2.itf.json"),
          source.files().stream().map(p -> p.getFileName().toString()).toList());
      assertEquals(6, source.toList().get(0).length());
    }
    assertFalse(Files.exists(tempDir), "temp dir deleted on close");
  }

  @Test
  void violationExitCodeIsSuccess(@TempDir Path dir) throws Exception {
    try (TraceSource source =
        ApalacheTraceGenerator.of(fake(dir, ApalacheTraceGenerator.EXIT_VIOLATION))
            .generate(CONFIG)) {
      assertEquals(2, source.size());
    }
  }

  @Test
  void otherExitCodesAreReportedWithOutput(@TempDir Path dir) throws Exception {
    ApalacheTraceGenerator generator = ApalacheTraceGenerator.of(fake(dir, 255));
    QuintException e = assertThrows(QuintException.class, () -> generator.generate(CONFIG).close());
    assertTrue(e.getMessage().startsWith("Apalache returned non-zero code."), e.getMessage());
    assertTrue(e.stderr().contains("fake apalache: --run-dir="), e.stderr());
    assertTrue(e.stderr().contains("check"), e.stderr());
  }

  @Test
  void missingExecutableIsReportedWithHint() {
    ApalacheTraceGenerator generator =
        ApalacheTraceGenerator.of(ApalacheCli.of("no-such-apalache"));
    QuintException e = assertThrows(QuintException.class, () -> generator.generate(CONFIG).close());
    assertTrue(e.getMessage().contains("Failed to execute Apalache command"), e.getMessage());
    assertTrue(e.getMessage().contains(ApalacheCli.APALACHE_BIN), e.getMessage());
  }

  @Test
  void rejectsQuintConfigs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ApalacheTraceGenerator.create().generate(RunConfig.of(Path.of("f.qnt"), "1")));
  }
}
