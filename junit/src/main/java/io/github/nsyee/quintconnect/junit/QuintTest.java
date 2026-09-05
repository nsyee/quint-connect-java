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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs a model-based test over traces generated with {@code quint test}, the counterpart of the
 * Rust {@code #[quint_test]} macro.
 *
 * <p>The annotated method is a {@link TestTemplate}: it must be {@code void} and declare a {@link
 * TraceReplay} parameter through which it hands its {@link
 * io.github.nsyee.quintconnect.driver.Driver} to the framework:
 *
 * <pre>{@code
 * @QuintTest(spec = "spec/two_phase_commit.qnt", test = "commitTest")
 * void commit(TraceReplay traces) {
 *   traces.replay(TwoPhaseCommitDriver::new);
 * }
 * }</pre>
 *
 * @see QuintRun
 * @see QuintConnectExtension
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@TestTemplate
@ExtendWith(QuintConnectExtension.class)
public @interface QuintTest {

  /**
   * Path of the Quint specification. Relative paths are resolved against the working directory (the
   * project directory under Gradle and Maven); {@code classpath:} prefixes a classpath resource,
   * which is copied to a temporary file. Required.
   */
  String spec();

  /** Main module, if not the default one. */
  String main() default "";

  /** Name of the test (run definition) to execute. Required. */
  String test();

  /** Number of traces to generate; {@code -1} leaves the Quint default. */
  int maxSamples() default -1;

  /**
   * Random seed; empty resolves through {@link io.github.nsyee.quintconnect.trace.Seeds#resolve()}
   * ({@code QUINT_SEED}, then random).
   */
  String seed() default "";

  /** Replays all traces in one test invocation instead of one invocation per trace. */
  boolean singleInvocation() default false;
}
