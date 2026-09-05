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

import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.runner.QuintConnect;
import io.github.nsyee.quintconnect.runner.QuintConnectException;
import io.github.nsyee.quintconnect.trace.GenConfig;
import io.github.nsyee.quintconnect.trace.RunConfig;
import io.github.nsyee.quintconnect.trace.Seeds;
import io.github.nsyee.quintconnect.trace.TestConfig;
import io.github.nsyee.quintconnect.trace.TraceSource;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * JUnit 5 extension behind {@link QuintRun} and {@link QuintTest}.
 *
 * <p>For every annotated method the extension generates the traces once and yields one test
 * invocation per trace, named {@code [Trace i/n] seed=…} (or a single invocation with {@code
 * singleInvocation = true}). Each invocation receives a {@link TraceReplay} parameter and must
 * replay it through its {@link io.github.nsyee.quintconnect.driver.Driver}.
 *
 * <p>Traces are generated with {@link QuintConnect#create()}, i.e. by spawning the Quint CLI. Tests
 * of the framework itself (and users with pre-generated traces) can substitute the runner for a
 * whole test class:
 *
 * <pre>{@code
 * @RegisterExtension
 * static final Extension runner =
 *     QuintConnectExtension.runner(QuintConnect.using(FileTraceGenerator.ofDirectory(traces)));
 * }</pre>
 */
public final class QuintConnectExtension implements TestTemplateInvocationContextProvider {

  static final Namespace NAMESPACE = Namespace.create(QuintConnectExtension.class);
  static final String RUNNER = "runner";
  static final String DRIVER = "driver";
  static final String SPEC = "spec";
  static final String TRACES = "traces";

  static final String MISSING_SPEC = "Missing required attribute `spec`";
  static final String MISSING_TEST = "Missing required attribute `test`";
  static final String BOTH_ANNOTATIONS =
      "A method must not be annotated with both @QuintRun and @QuintTest";
  static final String NOT_REPLAYED =
      "The test method must declare a TraceReplay parameter and call its replay(driver) method";

  /** Public no-arg constructor for {@link org.junit.jupiter.api.extension.ExtendWith}. */
  public QuintConnectExtension() {}

  /**
   * An extension that makes every {@link QuintRun} / {@link QuintTest} method of the test class use
   * {@code runner} instead of {@link QuintConnect#create()}. Register it on a {@code static} field
   * with {@link org.junit.jupiter.api.extension.RegisterExtension}.
   */
  public static Extension runner(QuintConnect runner) {
    Objects.requireNonNull(runner, "runner");
    return (BeforeAllCallback) context -> context.getStore(NAMESPACE).put(RUNNER, runner);
  }

  @Override
  public boolean supportsTestTemplate(ExtensionContext context) {
    Method method = context.getRequiredTestMethod();
    return AnnotationSupport.isAnnotated(method, QuintRun.class)
        || AnnotationSupport.isAnnotated(method, QuintTest.class);
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext context) {
    Method method = context.getRequiredTestMethod();
    Settings settings = Settings.from(method);
    Store store = context.getStore(NAMESPACE);
    QuintConnect runner = store.getOrDefault(RUNNER, QuintConnect.class, QuintConnect.create());

    SpecPaths.Resolved spec =
        SpecPaths.resolve(settings.spec(), context.getRequiredTestClass().getClassLoader());
    store.put(SPEC, spec);
    GenConfig config = settings.toConfig(spec.path());

    Logger logger = runner.logger();
    logger.title("Running model based tests for " + method.getName());
    logger.info(
        "Generating "
            + config.nTraces()
            + " traces using `"
            + config.seed()
            + "` as random seed ...");

    TraceSource source = runner.generator().generate(config);
    store.put(TRACES, source);
    if (source.size() == 0) {
      throw new QuintConnectException(QuintConnect.ZERO_TRACES);
    }
    List<ItfTrace> traces = source.toList();
    int total = traces.size();
    logger.info("Replaying traces ...");

    if (settings.singleInvocation()) {
      return Stream.of(
          new TraceInvocation(new TraceReplay(runner, traces, 1, total, config.seed(), store)));
    }
    return IntStream.range(0, total)
        .mapToObj(
            i ->
                new TraceInvocation(
                    new TraceReplay(
                        runner, List.of(traces.get(i)), i + 1, total, config.seed(), store)));
  }

  /** The attributes of a {@link QuintRun} or {@link QuintTest}, validated. */
  record Settings(
      String spec,
      Optional<String> main,
      Optional<String> init,
      Optional<String> step,
      Optional<String> test,
      int maxSamples,
      int maxSteps,
      String seed,
      boolean singleInvocation) {

    static Settings from(Method method) {
      Optional<QuintRun> run = AnnotationSupport.findAnnotation(method, QuintRun.class);
      Optional<QuintTest> test = AnnotationSupport.findAnnotation(method, QuintTest.class);
      if (run.isPresent() && test.isPresent()) {
        throw new ExtensionConfigurationException(BOTH_ANNOTATIONS);
      }
      if (run.isPresent()) {
        return from(run.get());
      }
      return from(
          test.orElseThrow(
              () ->
                  new ExtensionConfigurationException(
                      "Method is annotated with neither @QuintRun nor @QuintTest")));
    }

    static Settings from(QuintRun run) {
      requireAttribute(run.spec(), MISSING_SPEC);
      return new Settings(
          run.spec(),
          nonBlank(run.main()),
          nonBlank(run.init()),
          nonBlank(run.step()),
          Optional.empty(),
          run.maxSamples(),
          run.maxSteps(),
          run.seed(),
          run.singleInvocation());
    }

    static Settings from(QuintTest test) {
      requireAttribute(test.spec(), MISSING_SPEC);
      requireAttribute(test.test(), MISSING_TEST);
      return new Settings(
          test.spec(),
          nonBlank(test.main()),
          Optional.empty(),
          Optional.empty(),
          Optional.of(test.test()),
          test.maxSamples(),
          -1,
          test.seed(),
          test.singleInvocation());
    }

    GenConfig toConfig(Path specPath) {
      String resolvedSeed = Seeds.resolve(nonBlank(seed));
      if (test.isPresent()) {
        TestConfig config = TestConfig.of(specPath, test.get(), resolvedSeed);
        if (main.isPresent()) {
          config = config.withMain(main.get());
        }
        if (maxSamples >= 0) {
          config = config.withMaxSamples(maxSamples);
        }
        return config;
      }
      RunConfig config = RunConfig.of(specPath, resolvedSeed);
      if (main.isPresent()) {
        config = config.withMain(main.get());
      }
      if (init.isPresent()) {
        config = config.withInit(init.get());
      }
      if (step.isPresent()) {
        config = config.withStep(step.get());
      }
      if (maxSamples >= 0) {
        config = config.withMaxSamples(maxSamples);
      }
      if (maxSteps >= 0) {
        config = config.withMaxSteps(maxSteps);
      }
      return config;
    }

    private static void requireAttribute(String value, String message) {
      if (value.isBlank()) {
        throw new ExtensionConfigurationException(message);
      }
    }

    private static Optional<String> nonBlank(String value) {
      return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
  }

  /** One test invocation: resolves the {@link TraceReplay} parameter and checks it was used. */
  private static final class TraceInvocation
      implements TestTemplateInvocationContext, ParameterResolver, InvocationInterceptor {

    private final TraceReplay replay;

    TraceInvocation(TraceReplay replay) {
      this.replay = replay;
    }

    @Override
    public String getDisplayName(int invocationIndex) {
      return replay.displayName();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
      return List.of(this);
    }

    @Override
    public boolean supportsParameter(
        ParameterContext parameterContext, ExtensionContext extensionContext) {
      return parameterContext.getParameter().getType() == TraceReplay.class;
    }

    @Override
    public Object resolveParameter(
        ParameterContext parameterContext, ExtensionContext extensionContext) {
      return replay;
    }

    @Override
    public void interceptTestTemplateMethod(
        Invocation<Void> invocation,
        ReflectiveInvocationContext<Method> invocationContext,
        ExtensionContext extensionContext)
        throws Throwable {
      invocation.proceed();
      if (!replay.replayed()) {
        throw new ExtensionConfigurationException(NOT_REPLAYED);
      }
    }
  }
}
