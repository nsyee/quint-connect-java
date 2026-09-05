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

import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.runner.QuintConnect;
import io.github.nsyee.quintconnect.trace.GenConfig;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/**
 * {@link org.junit.jupiter.api.TestFactory} helper for configurations built programmatically:
 *
 * <pre>{@code
 * @TestFactory
 * Stream<DynamicTest> tictactoe() {
 *   return QuintDynamicTests.of(
 *       TicTacToeDriver::new,
 *       RunConfig.of(Path.of("spec/tictactoe.qnt"), Seeds.resolve()).withMaxSamples(10));
 * }
 * }</pre>
 *
 * <p>One dynamic test named {@code [Trace i/n] seed=…} is produced per generated trace. The stream
 * must be consumed in order (JUnit does) because the driver is shared across traces.
 */
public final class QuintDynamicTests {

  private QuintDynamicTests() {}

  /**
   * Generates traces with {@link QuintConnect#create()} and returns one dynamic test per trace.
   *
   * @param driver supplies the driver; called once
   * @param config what to generate
   * @param <S> the specification state type
   * @throws io.github.nsyee.quintconnect.runner.QuintConnectException if no trace was generated
   * @throws io.github.nsyee.quintconnect.trace.QuintException if trace generation fails
   */
  public static <S> Stream<DynamicTest> of(Supplier<? extends Driver<S>> driver, GenConfig config) {
    return of(QuintConnect.create(), driver, config);
  }

  /**
   * Generates traces with {@code runner} and returns one dynamic test per trace.
   *
   * @param runner the runner (trace generator, logger, mapper)
   * @param driver supplies the driver; called once
   * @param config what to generate
   * @param <S> the specification state type
   * @throws io.github.nsyee.quintconnect.runner.QuintConnectException if no trace was generated
   * @throws io.github.nsyee.quintconnect.trace.QuintException if trace generation fails
   */
  public static <S> Stream<DynamicTest> of(
      QuintConnect runner, Supplier<? extends Driver<S>> driver, GenConfig config) {
    Objects.requireNonNull(runner, "runner");
    return runner
        .traces(driver, config)
        .map(run -> DynamicTest.dynamicTest(run.displayName(), run::replay));
  }
}
