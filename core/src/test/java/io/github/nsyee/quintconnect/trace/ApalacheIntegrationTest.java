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
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration tests that spawn the real Apalache CLI ({@code -PskipApalache} to skip). */
@Tag("apalache")
class ApalacheIntegrationTest {

  private static final Path SPEC = FileTraceGeneratorTest.fixture("spec/Counter.tla");

  private static BigInteger count(ItfTrace trace, int step) {
    return ((ItfValue.Int) trace.states().get(step).value().fields().get("count")).value();
  }

  @Test
  void simulateProducesRequestedTraces() {
    ApalacheConfig config =
        ApalacheConfig.simulate(SPEC, "0x42")
            .withMaxRuns(2)
            .withLength(4)
            .withInvariants("NonNegative");
    Path tempDir;
    try (TraceSource source = ApalacheTraceGenerator.create().generate(config)) {
      tempDir = source.files().get(0).getParent();
      List<ItfTrace> traces = source.toList();
      assertEquals(2, traces.size());
      for (ItfTrace trace : traces) {
        assertTrue(trace.vars().containsAll(List.of("count", "mbt_action_taken")));
        assertEquals(5, trace.length());
        assertEquals(BigInteger.ZERO, count(trace, 0));
      }
    }
    assertFalse(tempDir.toFile().exists(), "temp dir deleted on close");
  }

  @Test
  void checkProducesCounterexamples() {
    ApalacheConfig config =
        ApalacheConfig.check(SPEC, "1")
            .withInvariants("BelowThree")
            .withLength(3)
            .withMaxErrors(2, "View");
    try (TraceSource source = ApalacheTraceGenerator.create().generate(config)) {
      List<ItfTrace> traces = source.toList();
      assertEquals(2, traces.size());
      for (ItfTrace trace : traces) {
        assertTrue(count(trace, trace.length() - 1).intValue() >= 3);
      }
    }
  }

  @Test
  void nonZeroExitIsReported() {
    ApalacheConfig config = ApalacheConfig.simulate(Path.of("DoesNotExist.tla"), "1");
    QuintException e =
        assertThrows(
            QuintException.class, () -> ApalacheTraceGenerator.create().generate(config).close());
    assertTrue(e.getMessage().startsWith("Apalache returned non-zero code."), e.getMessage());
    assertFalse(e.stderr().isBlank());
  }
}
