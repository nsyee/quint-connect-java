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

import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration tests that spawn the real Quint CLI. */
@Tag("quint")
class QuintTraceGeneratorTest {

  private static final Path SPEC = FileTraceGeneratorTest.fixture("spec/tictactoe.qnt");

  @Test
  void runProducesRequestedTraces() {
    RunConfig config = RunConfig.of(SPEC, "0x42").withMaxSamples(3).withMaxSteps(5);
    Path tempDir;
    try (TraceSource source = QuintTraceGenerator.create().generate(config)) {
      tempDir = source.files().get(0).getParent();
      List<ItfTrace> traces = source.toList();
      assertEquals(3, traces.size());
      for (ItfTrace trace : traces) {
        assertTrue(trace.vars().containsAll(List.of("board", "nextTurn", "mbt::actionTaken")));
        assertTrue(trace.length() >= 1 && trace.length() <= 6, "length " + trace.length());
        assertTrue(trace.states().get(0).value().fields().get("board") instanceof ItfValue.Map);
      }
    }
    assertFalse(tempDir.toFile().exists(), "temp dir deleted on close");
  }

  @Test
  void runIsDeterministicForSeed() {
    RunConfig config = RunConfig.of(SPEC, "0x1234").withMaxSamples(2).withMaxSteps(4);
    QuintTraceGenerator generator = QuintTraceGenerator.create();
    List<ItfTrace> first;
    List<ItfTrace> second;
    try (TraceSource source = generator.generate(config)) {
      first = source.toList();
    }
    try (TraceSource source = generator.generate(config)) {
      second = source.toList();
    }
    assertEquals(
        first.stream().map(ItfTrace::states).toList(),
        second.stream().map(ItfTrace::states).toList());
  }

  @Test
  void nonZeroExitIsReported() {
    RunConfig config = RunConfig.of(Path.of("does-not-exist.qnt"), "1").withMaxSamples(1);
    QuintException e =
        assertThrows(
            QuintException.class, () -> QuintTraceGenerator.create().generate(config).close());
    assertTrue(e.getMessage().startsWith("Quint returned non-zero code."), e.getMessage());
    assertFalse(e.stderr().isBlank());
  }

  @Test
  void missingExecutableIsReported() {
    RunConfig config = RunConfig.of(SPEC, "1").withMaxSamples(1);
    QuintTraceGenerator generator = QuintTraceGenerator.of(QuintCli.of("quint-does-not-exist"));
    QuintException e = assertThrows(QuintException.class, () -> generator.generate(config).close());
    assertTrue(e.getMessage().contains("Quint not found"), e.getMessage());
  }
}
