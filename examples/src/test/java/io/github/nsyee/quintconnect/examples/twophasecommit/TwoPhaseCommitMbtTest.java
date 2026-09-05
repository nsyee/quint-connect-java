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
package io.github.nsyee.quintconnect.examples.twophasecommit;

import io.github.nsyee.quintconnect.junit.QuintRun;
import io.github.nsyee.quintconnect.junit.QuintTest;
import io.github.nsyee.quintconnect.junit.TraceReplay;
import org.junit.jupiter.api.Tag;

/** Model-based tests of the two-phase commit {@link Node}s; need the Quint CLI on {@code PATH}. */
@Tag("quint")
class TwoPhaseCommitMbtTest {

  /** Random simulation with {@code quint run --mbt}. */
  @QuintRun(spec = "spec/two_phase_commit.qnt", maxSamples = 10)
  void simulation(TraceReplay traces) {
    traces.replay(TwoPhaseCommitDriver::new);
  }

  /** The {@code commitTest} run of the specification, executed with {@code quint test}. */
  @QuintTest(spec = "spec/two_phase_commit.qnt", test = "commitTest")
  void commit(TraceReplay traces) {
    traces.replay(TwoPhaseCommitDriver::new);
  }
}
