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
package io.github.nsyee.quintconnect.junit;

import static io.github.nsyee.quintconnect.junit.Fixtures.displayNames;
import static io.github.nsyee.quintconnect.junit.Fixtures.execute;
import static io.github.nsyee.quintconnect.junit.Fixtures.failure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.nsyee.quintconnect.junit.TicTacToeDrivers.Correct;
import io.github.nsyee.quintconnect.junit.TicTacToeDrivers.Diverging;
import io.github.nsyee.quintconnect.runner.QuintConnectException;
import io.github.nsyee.quintconnect.trace.RunConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.Event;

class QuintDynamicTestsTest {

  static final RunConfig CONFIG = RunConfig.of(Path.of("spec/tictactoe.qnt"), "0x2a");

  @Tag("fixture")
  static class Factories {
    static Correct driver;

    @TestFactory
    Stream<DynamicTest> passing() {
      return QuintDynamicTests.of(
          Fixtures.runner("tictactoe", "tictactoe"),
          () -> {
            driver = new Correct();
            return driver;
          },
          CONFIG);
    }

    @TestFactory
    Stream<DynamicTest> failing() {
      return QuintDynamicTests.of(Fixtures.runner("tictactoe"), Diverging::new, CONFIG);
    }
  }

  @Test
  void oneDynamicTestPerTrace() {
    EngineExecutionResults results = execute(Factories.class);

    results.testEvents().assertStatistics(stats -> stats.started(3).succeeded(2).failed(1));
    assertEquals(
        List.of("[Trace 1/2] seed=0x2a", "[Trace 2/2] seed=0x2a"),
        displayNames(results.testEvents().succeeded().list()));
    assertEquals(12, Factories.driver.actions.size());

    Event failed = results.testEvents().failed().list().get(0);
    assertEquals("[Trace 1/1] seed=0x2a", failed.getTestDescriptor().getDisplayName());
    QuintConnectException e = assertInstanceOf(QuintConnectException.class, failure(failed));
    assertEquals(OptionalInt.of(1), e.traceIndex());
  }
}
