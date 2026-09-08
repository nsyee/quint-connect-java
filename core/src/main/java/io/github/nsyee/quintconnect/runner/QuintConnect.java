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

import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.driver.DriverException;
import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.itf.ItfState;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.itf.ItfValue;
import io.github.nsyee.quintconnect.itf.mapper.ItfMapper;
import io.github.nsyee.quintconnect.itf.mapper.ItfMappingException;
import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.runner.QuintConnectException.Context;
import io.github.nsyee.quintconnect.trace.GenConfig;
import io.github.nsyee.quintconnect.trace.QuintTraceGenerator;
import io.github.nsyee.quintconnect.trace.TraceGenerator;
import io.github.nsyee.quintconnect.trace.TraceSource;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Runs model-based tests: generates traces, replays them through a {@link Driver} and checks the
 * implementation state against the specification state after every step.
 *
 * <p>The static {@link #run} is the one-call entry point, equivalent to {@code runner::run_test} in
 * the Rust crate:
 *
 * <pre>{@code
 * QuintConnect.run(
 *     "tictactoe",
 *     TicTacToeDriver::new,
 *     RunConfig.of(Path.of("spec/tictactoe.qnt"), Seeds.resolve()).withMaxSamples(10));
 * }</pre>
 *
 * <p>Instances allow swapping the {@link TraceGenerator} (e.g. {@link
 * io.github.nsyee.quintconnect.trace.FileTraceGenerator} for pre-generated traces), the {@link
 * Logger} and the {@link ItfMapper}; {@link #traces} exposes one {@link TraceRun} per trace for
 * test frameworks that want per-trace reporting.
 *
 * <p>A single driver instance, obtained once from the supplier, is reused across all traces, as in
 * the Rust crate; the {@code init} action is expected to reset it.
 */
public final class QuintConnect {

  /** Message when the generator produced no trace. */
  public static final String ZERO_TRACES =
      """
      Trace generation produced zero traces.
      Please check your specification and/or your test configuration.""";

  /** Message when a step has no action name. */
  static final String ANONYMOUS_ACTION =
      """
      An anonymous action was found!
      Please make sure all actions in the specification are properly named.
      Check the docs for tips and tricks on nondeterminism.""";

  /** Summary message of a state divergence. */
  static final String STATE_INVARIANT_FAILED = "State invariant failed";

  private final TraceGenerator generator;
  private final Logger logger;
  private final ItfMapper mapper;

  private QuintConnect(TraceGenerator generator, Logger logger, ItfMapper mapper) {
    this.generator = Objects.requireNonNull(generator, "generator");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  /**
   * A runner that spawns the Quint CLI ({@link QuintTraceGenerator#create()}, located lazily on
   * first use), logs with {@link Logger#standard()} and maps with {@link
   * ItfMapper#defaultMapper()}.
   */
  public static QuintConnect create() {
    return using(config -> QuintTraceGenerator.create().generate(config));
  }

  /** A runner over the given trace generator with the standard logger and default mapper. */
  public static QuintConnect using(TraceGenerator generator) {
    return new QuintConnect(generator, Logger.standard(), ItfMapper.defaultMapper());
  }

  /** Returns a copy using {@code logger}. */
  public QuintConnect withLogger(Logger logger) {
    return new QuintConnect(generator, logger, mapper);
  }

  /** Returns a copy using {@code mapper} to deserialise specification states. */
  public QuintConnect withMapper(ItfMapper mapper) {
    return new QuintConnect(generator, logger, mapper);
  }

  /** The trace generator in use. */
  public TraceGenerator generator() {
    return generator;
  }

  /** The logger in use. */
  public Logger logger() {
    return logger;
  }

  /** The mapper in use. */
  public ItfMapper mapper() {
    return mapper;
  }

  /**
   * Generates traces with the Quint CLI and replays them, like {@code runner::run_test}.
   *
   * @param testName the name printed in the report
   * @param driver supplies the driver; called once
   * @param config what to generate
   * @param <S> the specification state type
   * @throws QuintConnectException if the run fails
   * @throws io.github.nsyee.quintconnect.trace.QuintException if trace generation fails
   */
  public static <S> void run(
      String testName, Supplier<? extends Driver<S>> driver, GenConfig config) {
    create().runTest(testName, driver, config);
  }

  /**
   * Generates traces and replays them, reporting {@code [OK]} / {@code [FAIL]} and the seed to
   * reproduce a failure.
   *
   * @param testName the name printed in the report
   * @param driver supplies the driver; called once
   * @param config what to generate
   * @param <S> the specification state type
   * @throws QuintConnectException if the run fails
   * @throws io.github.nsyee.quintconnect.trace.QuintException if trace generation fails
   */
  public <S> void runTest(String testName, Supplier<? extends Driver<S>> driver, GenConfig config) {
    Objects.requireNonNull(testName, "testName");
    Objects.requireNonNull(driver, "driver");
    Objects.requireNonNull(config, "config");

    logger.title("Running model based tests for " + testName);
    logger.info(
        "Generating "
            + config.nTraces()
            + " traces using `"
            + config.seed()
            + "` as random seed ...");

    try (TraceSource source = generator.generate(config)) {
      try {
        replayTraces(driver.get(), source);
      } catch (RuntimeException e) {
        logger.error("[FAIL] " + testName + " ");
        logger.error("Reproduce this error with `QUINT_SEED=" + config.seed() + "`\n");
        throw e;
      }
      logger.success("[OK] " + testName);
    }
  }

  /**
   * Generates traces and returns one {@link TraceRun} per trace, to be replayed by the caller.
   *
   * <p>The returned stream must be closed (e.g. with try-with-resources or by the consuming test
   * engine) to release the generated files. The driver is obtained once and shared by all runs,
   * which must therefore be replayed sequentially and in order.
   *
   * @param driver supplies the driver; called once
   * @param config what to generate
   * @param <S> the specification state type
   * @throws QuintConnectException if no trace was generated
   * @throws io.github.nsyee.quintconnect.trace.QuintException if trace generation fails
   */
  public <S> Stream<TraceRun> traces(Supplier<? extends Driver<S>> driver, GenConfig config) {
    Objects.requireNonNull(driver, "driver");
    Objects.requireNonNull(config, "config");

    TraceSource source = generator.generate(config);
    int count = source.size();
    if (count == 0) {
      source.close();
      throw new QuintConnectException(ZERO_TRACES);
    }
    Driver<S> instance = driver.get();
    Iterator<ItfTrace> traces = source.traces().iterator();
    return Stream.iterate(1, i -> i <= count, i -> i + 1)
        .map(
            i -> {
              ItfTrace trace = traces.next();
              return new TraceRun(
                  i, count, config.seed(), trace, () -> replayTrace(instance, trace, i));
            })
        .onClose(source::close);
  }

  /**
   * Replays every trace of {@code source} through {@code driver}, in order.
   *
   * @throws QuintConnectException if no trace was generated, the driver fails or a state diverges
   */
  public <S> void replayTraces(Driver<S> driver, TraceSource source) {
    Objects.requireNonNull(driver, "driver");
    Objects.requireNonNull(source, "source");

    logger.info("Replaying traces ...");
    if (source.size() == 0) {
      throw new QuintConnectException(ZERO_TRACES);
    }
    int t = 1;
    for (Iterator<ItfTrace> it = source.traces().iterator(); it.hasNext(); t++) {
      replayTrace(driver, it.next(), t);
    }
  }

  /**
   * Replays one trace through {@code driver}: for every state, extracts the {@link Step}, rejects
   * anonymous actions, calls {@link Driver#step} and checks the state.
   *
   * @param driver the driver
   * @param trace the trace
   * @param traceIndex the 1-based index used in logs and error messages
   * @throws QuintConnectException if the driver fails or a state diverges
   */
  public <S> void replayTrace(Driver<S> driver, ItfTrace trace, int traceIndex) {
    Objects.requireNonNull(driver, "driver");
    Objects.requireNonNull(trace, "trace");

    logger.trace(1, "[Trace " + traceIndex + "]");
    Context context = Context.ofTrace(traceIndex);
    for (ItfState state : trace.states()) {
      Context at = context.atStep(state.index());
      if (logger.isEnabled(2)) {
        logger.trace(2, "Deriving step from:\n" + state.value().display() + "\n");
      }
      Step step;
      try {
        step = Step.from(state, driver.config());
      } catch (DriverException e) {
        throw new QuintConnectException(e.getMessage(), at, e);
      }
      if (logger.isEnabled(1)) {
        logger.trace(1, "[Step " + state.index() + "]\n" + step + "\n");
      }
      if (step.isAnonymous()) {
        throw new QuintConnectException(ANONYMOUS_ACTION, at);
      }
      at = at.withAction(step.action());
      try {
        driver.step(step);
      } catch (QuintConnectException e) {
        throw e;
      } catch (Exception e) {
        throw new QuintConnectException(describe(e), at, e);
      }
      checkState(driver, step, at);
    }
  }

  private <S> void checkState(Driver<S> driver, Step step, Context context) {
    Class<S> type = driver.stateType();
    if (type == Void.class || type == void.class) {
      return;
    }
    if (logger.isEnabled(2)) {
      logger.trace(2, "Extracting state from:\n" + step.specState().display() + "\n");
    }
    S spec;
    try {
      spec = mapper.stateFromItf(step.specState(), type);
    } catch (ItfMappingException e) {
      throw new QuintConnectException(e.getMessage(), context, e);
    }
    S impl;
    try {
      impl = driver.state();
    } catch (RuntimeException e) {
      throw new QuintConnectException(describe(e), context, e);
    }
    if (!Objects.equals(spec, impl)) {
      StateDiff diff = StateDiff.of(canonical(spec), canonical(impl));
      logger.error("Specification and implementation states diverge");
      logger.trace(1, diff.unified() + "\n");
      throw new QuintConnectException(STATE_INVARIANT_FAILED, context, diff);
    }
  }

  private ItfValue canonical(Object state) {
    try {
      return mapper.toItf(state);
    } catch (ItfMappingException e) {
      return new ItfValue.Unserializable(String.valueOf(state));
    }
  }

  private static String describe(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getName() : message;
  }
}
