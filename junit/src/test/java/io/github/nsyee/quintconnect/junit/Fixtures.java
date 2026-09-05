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

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.runner.QuintConnect;
import io.github.nsyee.quintconnect.trace.FileTraceGenerator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/** Shared helpers for the EngineTestKit-driven tests. */
final class Fixtures {

  /** Spec path relative to the module directory, which is Gradle's working directory for tests. */
  static final String RELATIVE_SPEC = "../core/src/test/resources/spec/tictactoe.qnt";

  static final String CLASSPATH_SPEC = "classpath:spec/tictactoe.qnt";

  static final ByteArrayOutputStream LOG = new ByteArrayOutputStream();

  private Fixtures() {}

  static Path fixture(String name) {
    try {
      return Path.of(Fixtures.class.getResource("/itf/" + name + ".itf.json").toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A runner replaying the named fixtures, logging (uncoloured, verbosity 1) into {@link #LOG}. */
  static QuintConnect runner(String... fixtures) {
    Path[] files = Arrays.stream(fixtures).map(Fixtures::fixture).toArray(Path[]::new);
    Logger logger = new Logger(new PrintStream(LOG, true, StandardCharsets.UTF_8), 1, false);
    return QuintConnect.using(FileTraceGenerator.of(files)).withLogger(logger);
  }

  static String log() {
    return LOG.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
  }

  static EngineExecutionResults execute(Class<?> testClass) {
    LOG.reset();
    return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute();
  }

  static List<String> displayNames(List<Event> events) {
    return events.stream().map(e -> e.getTestDescriptor().getDisplayName()).toList();
  }

  static Throwable failure(Event event) {
    return event.getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow();
  }
}
