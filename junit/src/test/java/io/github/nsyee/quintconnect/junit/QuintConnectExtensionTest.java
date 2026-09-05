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

import static io.github.nsyee.quintconnect.junit.Fixtures.CLASSPATH_SPEC;
import static io.github.nsyee.quintconnect.junit.Fixtures.RELATIVE_SPEC;
import static io.github.nsyee.quintconnect.junit.Fixtures.displayNames;
import static io.github.nsyee.quintconnect.junit.Fixtures.execute;
import static io.github.nsyee.quintconnect.junit.Fixtures.failure;
import static io.github.nsyee.quintconnect.junit.Fixtures.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.testkit.engine.EventConditions.container;
import static org.junit.platform.testkit.engine.EventConditions.displayName;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import io.github.nsyee.quintconnect.junit.TicTacToeDrivers.Correct;
import io.github.nsyee.quintconnect.junit.TicTacToeDrivers.Diverging;
import io.github.nsyee.quintconnect.junit.TicTacToeDrivers.Throwing;
import io.github.nsyee.quintconnect.runner.QuintConnect;
import io.github.nsyee.quintconnect.runner.QuintConnectException;
import io.github.nsyee.quintconnect.trace.RunConfig;
import io.github.nsyee.quintconnect.trace.TestConfig;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.Event;

class QuintConnectExtensionTest {

  @AfterEach
  void clearSeed() {
    System.clearProperty("QUINT_SEED");
  }

  // --- fixture test classes, executed through EngineTestKit -------------------------------------

  @Tag("fixture")
  static class PerTrace {
    @RegisterExtension
    static final Extension runner =
        QuintConnectExtension.runner(Fixtures.runner("tictactoe", "tictactoe", "tictactoe"));

    static int driversCreated;
    static Correct driver;

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a", maxSamples = 3)
    void tictactoe(TraceReplay traces) {
      assertEquals(1, traces.traces().size());
      assertEquals(3, traces.total());
      assertEquals("0x2a", traces.seed());
      traces.replay(
          () -> {
            driversCreated++;
            driver = new Correct();
            return driver;
          });
    }

    @QuintTest(spec = CLASSPATH_SPEC, test = "someTest", seed = "0x2a", maxSamples = 3)
    void commit(TraceReplay traces) {
      traces.replay(new Correct());
    }
  }

  @Tag("fixture")
  static class Failing {
    @RegisterExtension
    static final Extension runner =
        QuintConnectExtension.runner(Fixtures.runner("tictactoe", "tictactoe"));

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a")
    void diverging(TraceReplay traces) {
      traces.replay(Diverging::new);
    }

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a")
    void throwing(TraceReplay traces) {
      traces.replay(Throwing::new);
    }
  }

  @Tag("fixture")
  static class Single {
    @RegisterExtension
    static final Extension runner =
        QuintConnectExtension.runner(Fixtures.runner("tictactoe", "tictactoe", "tictactoe"));

    static Correct driver;

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a", singleInvocation = true)
    void passing(TraceReplay traces) {
      assertEquals(3, traces.traces().size());
      driver = new Correct();
      traces.replay(driver);
    }

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a", singleInvocation = true)
    void failing(TraceReplay traces) {
      traces.replay(Diverging::new);
    }
  }

  @Tag("fixture")
  static class RelativeSpecAndSeedProperty {
    @RegisterExtension
    static final Extension runner = QuintConnectExtension.runner(Fixtures.runner("tictactoe"));

    @QuintRun(spec = RELATIVE_SPEC)
    void tictactoe(TraceReplay traces) {
      traces.replay(Correct::new);
    }
  }

  @Tag("fixture")
  static class ZeroTraces {
    @RegisterExtension
    static final Extension runner = QuintConnectExtension.runner(Fixtures.runner());

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a")
    void tictactoe(TraceReplay traces) {
      traces.replay(Correct::new);
    }
  }

  @Tag("fixture")
  static class Invalid {
    @RegisterExtension
    static final Extension runner = QuintConnectExtension.runner(Fixtures.runner("tictactoe"));

    @QuintRun(spec = "")
    void missingSpec(TraceReplay traces) {
      traces.replay(Correct::new);
    }

    @QuintTest(spec = CLASSPATH_SPEC, test = "")
    void missingTest(TraceReplay traces) {
      traces.replay(Correct::new);
    }

    @QuintRun(spec = CLASSPATH_SPEC)
    @QuintTest(spec = CLASSPATH_SPEC, test = "t")
    void bothAnnotations(TraceReplay traces) {
      traces.replay(Correct::new);
    }

    @QuintRun(spec = "no/such/file.qnt")
    void missingFile(TraceReplay traces) {
      traces.replay(Correct::new);
    }

    @QuintRun(spec = "classpath:no/such/resource.qnt")
    void missingResource(TraceReplay traces) {
      traces.replay(Correct::new);
    }

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a")
    void neverReplays(TraceReplay traces) {}

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a")
    void noParameter() {}

    @QuintRun(spec = CLASSPATH_SPEC, seed = "0x2a")
    void replaysTwice(TraceReplay traces) {
      traces.replay(Correct::new);
      traces.replay(Correct::new);
    }
  }

  @Tag("fixture")
  static class RealCli {
    @QuintRun(spec = RELATIVE_SPEC, maxSamples = 2, maxSteps = 5, seed = "0x42")
    void tictactoe(TraceReplay traces) {
      traces.replay(Correct::new);
    }
  }

  // --- tests
  // --------------------------------------------------------------------------------------

  @Test
  void oneInvocationPerTraceWithSharedDriver() throws IOException {
    PerTrace.driversCreated = 0;
    long tempDirsBefore = tempSpecDirs();
    EngineExecutionResults results = execute(PerTrace.class);
    assertEquals(tempDirsBefore, tempSpecDirs(), "classpath spec copies are cleaned up");

    results.testEvents().assertStatistics(stats -> stats.started(6).succeeded(6).failed(0));
    results.containerEvents().assertStatistics(stats -> stats.failed(0));
    List<String> names = displayNames(results.testEvents().succeeded().list());
    assertEquals(2, names.stream().filter("[Trace 1/3] seed=0x2a"::equals).count());
    assertEquals(
        List.of("[Trace 1/3] seed=0x2a", "[Trace 2/3] seed=0x2a", "[Trace 3/3] seed=0x2a"),
        names.subList(0, 3));

    assertEquals(1, PerTrace.driversCreated, "driver supplier called once per method");
    assertEquals(18, PerTrace.driver.actions.size(), "same driver replayed all three traces");

    String log = log();
    assertTrue(log.contains("== Running model based tests for tictactoe\n"), log);
    assertTrue(log.contains("== Running model based tests for commit\n"), log);
    assertTrue(log.contains("   Generating 3 traces using `0x2a` as random seed ...\n"), log);
    assertTrue(log.contains("   [OK] [Trace 2/3] seed=0x2a\n"), log);
  }

  @Test
  void perTraceFailuresCarryTraceIndex() {
    EngineExecutionResults results = execute(Failing.class);

    results.testEvents().assertStatistics(stats -> stats.started(4).failed(4));
    List<Event> failed = results.testEvents().failed().list();
    assertEquals(
        List.of(
            "[Trace 1/2] seed=0x2a",
            "[Trace 2/2] seed=0x2a",
            "[Trace 1/2] seed=0x2a",
            "[Trace 2/2] seed=0x2a"),
        displayNames(failed));

    QuintConnectException first =
        assertInstanceOf(QuintConnectException.class, failure(failed.get(0)));
    assertEquals(OptionalInt.of(1), first.traceIndex());
    assertEquals(OptionalInt.of(2), first.stepIndex());
    assertEquals(Optional.of("MoveO"), first.action());
    assertTrue(
        first
            .getMessage()
            .startsWith("State invariant failed (trace 1, step 2, action `MoveO`)\n"));
    QuintConnectException second =
        assertInstanceOf(QuintConnectException.class, failure(failed.get(1)));
    assertEquals(OptionalInt.of(2), second.traceIndex());

    String log = log();
    assertTrue(log.contains("   [FAIL] [Trace 1/2] seed=0x2a \n"), log);
    assertTrue(log.contains("   Reproduce this error with `QUINT_SEED=0x2a`\n"), log);
  }

  @Test
  void singleInvocationReplaysAllTraces() {
    EngineExecutionResults results = execute(Single.class);

    results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(1).failed(1));
    assertEquals(
        List.of("[Traces 1-3] seed=0x2a"), displayNames(results.testEvents().succeeded().list()));
    assertEquals(18, Single.driver.actions.size());

    Event failed = results.testEvents().failed().list().get(0);
    assertEquals("[Traces 1-3] seed=0x2a", failed.getTestDescriptor().getDisplayName());
    QuintConnectException e = assertInstanceOf(QuintConnectException.class, failure(failed));
    assertEquals(OptionalInt.of(1), e.traceIndex());
  }

  @Test
  void relativeSpecAndSeedFromSystemProperty() {
    System.setProperty("QUINT_SEED", "0xbeef");
    EngineExecutionResults results = execute(RelativeSpecAndSeedProperty.class);

    results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
    assertEquals(
        List.of("[Trace 1/1] seed=0xbeef"), displayNames(results.testEvents().succeeded().list()));
  }

  @Test
  void zeroTracesFailTheTemplate() {
    EngineExecutionResults results = execute(ZeroTraces.class);

    results.testEvents().assertStatistics(stats -> stats.started(0));
    results
        .containerEvents()
        .assertThatEvents()
        .haveExactly(
            1,
            event(
                container("tictactoe"),
                finishedWithFailure(
                    instanceOf(QuintConnectException.class), message(QuintConnect.ZERO_TRACES))));
  }

  @Test
  void invalidConfigurationsFailWithRustMessages() {
    EngineExecutionResults results = execute(Invalid.class);

    results
        .containerEvents()
        .assertThatEvents()
        .haveExactly(
            1,
            event(
                container("missingSpec"),
                finishedWithFailure(
                    instanceOf(ExtensionConfigurationException.class),
                    message("Missing required attribute `spec`"))))
        .haveExactly(
            1,
            event(
                container("missingTest"),
                finishedWithFailure(
                    instanceOf(ExtensionConfigurationException.class),
                    message("Missing required attribute `test`"))))
        .haveExactly(
            1,
            event(
                container("bothAnnotations"),
                finishedWithFailure(
                    instanceOf(ExtensionConfigurationException.class),
                    message(QuintConnectExtension.BOTH_ANNOTATIONS))))
        .haveExactly(
            1,
            event(
                container("missingFile"),
                finishedWithFailure(
                    instanceOf(JUnitException.class),
                    message(m -> m.startsWith("Specification file not found: no/such/file.qnt")))))
        .haveExactly(
            1,
            event(
                container("missingResource"),
                finishedWithFailure(
                    instanceOf(JUnitException.class),
                    message(
                        "Specification resource not found on the classpath: no/such/resource.qnt"))));

    results
        .testEvents()
        .assertThatEvents()
        .haveExactly(
            2,
            event(
                displayName("[Trace 1/1] seed=0x2a"),
                finishedWithFailure(
                    instanceOf(ExtensionConfigurationException.class),
                    message(QuintConnectExtension.NOT_REPLAYED))))
        .haveExactly(
            1,
            event(
                displayName("[Trace 1/1] seed=0x2a"),
                finishedWithFailure(
                    instanceOf(IllegalStateException.class),
                    message(m -> m.startsWith("replay() was already called")))));
  }

  @Test
  void runSettingsMapToRunConfig() throws Exception {
    QuintRun run =
        Holder.class.getDeclaredMethod("run", TraceReplay.class).getAnnotation(QuintRun.class);
    RunConfig config =
        assertInstanceOf(
            RunConfig.class,
            QuintConnectExtension.Settings.from(run).toConfig(Path.of("spec.qnt")));
    assertEquals(
        new RunConfig(
            Path.of("spec.qnt"),
            Optional.of("Main"),
            Optional.of("start"),
            Optional.of("next"),
            OptionalInt.of(7),
            OptionalInt.of(9),
            "0x1"),
        config);

    Method defaults = Holder.class.getDeclaredMethod("defaults", TraceReplay.class);
    System.setProperty("QUINT_SEED", "0xabc");
    RunConfig cfg =
        assertInstanceOf(
            RunConfig.class,
            QuintConnectExtension.Settings.from(defaults).toConfig(Path.of("s.qnt")));
    assertEquals(RunConfig.of(Path.of("s.qnt"), "0xabc"), cfg);
    assertSame(false, QuintConnectExtension.Settings.from(defaults).singleInvocation());
  }

  @Test
  void testSettingsMapToTestConfig() throws Exception {
    QuintTest test =
        Holder.class.getDeclaredMethod("test", TraceReplay.class).getAnnotation(QuintTest.class);
    TestConfig config =
        assertInstanceOf(
            TestConfig.class,
            QuintConnectExtension.Settings.from(test).toConfig(Path.of("spec.qnt")));
    assertEquals(
        new TestConfig(
            Path.of("spec.qnt"), Optional.of("Main"), "myTest", OptionalInt.of(5), "0x2"),
        config);
  }

  @Test
  @Tag("quint")
  void generatesTracesWithTheQuintCliByDefault() {
    EngineExecutionResults results = execute(RealCli.class);

    results.containerEvents().assertStatistics(stats -> stats.failed(0));
    results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(2));
    assertEquals(
        List.of("[Trace 1/2] seed=0x42", "[Trace 2/2] seed=0x42"),
        displayNames(results.testEvents().succeeded().list()));
  }

  private static long tempSpecDirs() throws IOException {
    try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return entries
          .filter(p -> p.getFileName().toString().startsWith("quint-connect-spec-"))
          .count();
    }
  }

  /** Carries annotations for the {@code Settings} tests; never executed. */
  @Tag("fixture")
  static class Holder {
    @QuintRun(
        spec = "spec.qnt",
        main = "Main",
        init = "start",
        step = "next",
        maxSamples = 7,
        maxSteps = 9,
        seed = "0x1")
    void run(TraceReplay traces) {}

    @QuintRun(spec = "spec.qnt")
    void defaults(TraceReplay traces) {}

    @QuintTest(spec = "spec.qnt", main = "Main", test = "myTest", maxSamples = 5, seed = "0x2")
    void test(TraceReplay traces) {}
  }
}
