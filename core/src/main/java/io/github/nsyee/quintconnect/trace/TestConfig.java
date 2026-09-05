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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Configuration for generating traces with {@code quint test}.
 *
 * @param spec path of the Quint specification
 * @param main main module, if not the default
 * @param test name of the test (run definition) to execute; matched as {@code ^test$}
 * @param maxSamples number of traces; defaults to {@link GenConfig#DEFAULT_TRACES}
 * @param seed random seed
 */
public record TestConfig(
    Path spec, Optional<String> main, String test, OptionalInt maxSamples, String seed)
    implements GenConfig {

  /** Validates that no component is {@code null}. */
  public TestConfig {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(main, "main");
    Objects.requireNonNull(test, "test");
    Objects.requireNonNull(maxSamples, "maxSamples");
    Objects.requireNonNull(seed, "seed");
  }

  /** Creates a configuration with only the spec, test name and seed set. */
  public static TestConfig of(Path spec, String test, String seed) {
    return new TestConfig(spec, Optional.empty(), test, OptionalInt.empty(), seed);
  }

  /** Returns a copy with the given main module. */
  public TestConfig withMain(String main) {
    return new TestConfig(spec, Optional.of(main), test, maxSamples, seed);
  }

  /** Returns a copy with the given number of samples. */
  public TestConfig withMaxSamples(int maxSamples) {
    return new TestConfig(spec, main, test, OptionalInt.of(maxSamples), seed);
  }

  /** Returns a copy with the given seed. */
  public TestConfig withSeed(String seed) {
    return new TestConfig(spec, main, test, maxSamples, seed);
  }

  @Override
  public int nTraces() {
    return maxSamples.orElse(DEFAULT_TRACES);
  }

  @Override
  public List<String> toCommand(Path outDir) {
    List<String> cmd = new ArrayList<>();
    cmd.add("test");
    cmd.add(spec.toString());
    cmd.add("--seed");
    cmd.add(seed);
    cmd.add("--match");
    cmd.add("^" + test + "$");
    cmd.add("--max-samples");
    cmd.add(Integer.toString(nTraces()));
    cmd.add("--out-itf");
    cmd.add(outDir.resolve("test_{seq}.itf.json").toString());
    cmd.add("--verbosity");
    cmd.add("0");
    Commands.optArg(cmd, "--main", main);
    return cmd;
  }
}
