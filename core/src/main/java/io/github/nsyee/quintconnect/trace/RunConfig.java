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
 * Configuration for generating traces with {@code quint run} in simulation mode ({@code --mbt}).
 *
 * @param spec path of the Quint specification
 * @param main main module, if not the default
 * @param init init action, if not {@code init}
 * @param step step action, if not {@code step}
 * @param maxSamples number of traces (also passed as {@code --n-traces}); defaults to {@link
 *     GenConfig#DEFAULT_TRACES}
 * @param maxSteps maximum number of steps per trace
 * @param seed random seed
 */
public record RunConfig(
    Path spec,
    Optional<String> main,
    Optional<String> init,
    Optional<String> step,
    OptionalInt maxSamples,
    OptionalInt maxSteps,
    String seed)
    implements GenConfig {

  /** Validates that no component is {@code null}. */
  public RunConfig {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(main, "main");
    Objects.requireNonNull(init, "init");
    Objects.requireNonNull(step, "step");
    Objects.requireNonNull(maxSamples, "maxSamples");
    Objects.requireNonNull(maxSteps, "maxSteps");
    Objects.requireNonNull(seed, "seed");
  }

  /** Creates a configuration with only the spec and seed set. */
  public static RunConfig of(Path spec, String seed) {
    return new RunConfig(
        spec,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        OptionalInt.empty(),
        OptionalInt.empty(),
        seed);
  }

  /** Returns a copy with the given main module. */
  public RunConfig withMain(String main) {
    return new RunConfig(spec, Optional.of(main), init, step, maxSamples, maxSteps, seed);
  }

  /** Returns a copy with the given init action. */
  public RunConfig withInit(String init) {
    return new RunConfig(spec, main, Optional.of(init), step, maxSamples, maxSteps, seed);
  }

  /** Returns a copy with the given step action. */
  public RunConfig withStep(String step) {
    return new RunConfig(spec, main, init, Optional.of(step), maxSamples, maxSteps, seed);
  }

  /** Returns a copy with the given number of samples. */
  public RunConfig withMaxSamples(int maxSamples) {
    return new RunConfig(spec, main, init, step, OptionalInt.of(maxSamples), maxSteps, seed);
  }

  /** Returns a copy with the given maximum number of steps. */
  public RunConfig withMaxSteps(int maxSteps) {
    return new RunConfig(spec, main, init, step, maxSamples, OptionalInt.of(maxSteps), seed);
  }

  /** Returns a copy with the given seed. */
  public RunConfig withSeed(String seed) {
    return new RunConfig(spec, main, init, step, maxSamples, maxSteps, seed);
  }

  @Override
  public int nTraces() {
    return maxSamples.orElse(DEFAULT_TRACES);
  }

  @Override
  public List<String> toCommand(Path outDir) {
    String nTraces = Integer.toString(nTraces());
    List<String> cmd = new ArrayList<>();
    cmd.add("run");
    cmd.add(spec.toString());
    cmd.add("--seed");
    cmd.add(seed);
    cmd.add("--max-samples");
    cmd.add(nTraces);
    cmd.add("--n-traces");
    cmd.add(nTraces);
    cmd.add("--out-itf");
    cmd.add(outDir.resolve("run_{seq}.itf.json").toString());
    cmd.add("--mbt");
    cmd.add("--verbosity");
    cmd.add("0");
    Commands.optArg(cmd, "--main", main);
    Commands.optArg(cmd, "--init", init);
    Commands.optArg(cmd, "--step", step);
    Commands.optArg(cmd, "--max-steps", maxSteps);
    return cmd;
  }
}
