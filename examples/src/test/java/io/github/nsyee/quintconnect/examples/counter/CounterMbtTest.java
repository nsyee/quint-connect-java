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
package io.github.nsyee.quintconnect.examples.counter;

import io.github.nsyee.quintconnect.runner.QuintConnect;
import io.github.nsyee.quintconnect.trace.ApalacheConfig;
import io.github.nsyee.quintconnect.trace.ApalacheTraceGenerator;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Model-based tests of {@link Counter} against a TLA+ specification; need {@code apalache-mc} on
 * {@code PATH} (or {@code APALACHE_BIN}).
 */
@Tag("apalache")
class CounterMbtTest {

  private static final Path SPEC = Path.of("spec/Counter.tla");
  private static final QuintConnect RUNNER = QuintConnect.using(ApalacheTraceGenerator.create());

  /** Random simulation with {@code apalache-mc simulate}. */
  @Test
  void simulation() {
    ApalacheConfig config = ApalacheConfig.simulate(SPEC, "0x2a").withMaxRuns(5).withLength(8);
    RUNNER.runTest("Counter simulate", CounterDriver::new, config);
  }

  /** Counterexamples to {@code BelowThree} found by {@code apalache-mc check}. */
  @Test
  void counterexamples() {
    ApalacheConfig config =
        ApalacheConfig.check(SPEC, "1")
            .withInvariants("BelowThree")
            .withLength(4)
            .withMaxErrors(3, "View");
    RUNNER.runTest("Counter check", CounterDriver::new, config);
  }
}
