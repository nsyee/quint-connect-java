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
import java.util.List;

/**
 * Configuration of a trace generation run.
 *
 * <p>Mirrors the {@code Config} trait of the Rust crate: every configuration knows its seed, how
 * many traces it asks for and how to turn itself into a command line for its trace generator
 * ({@code quint run} / {@code quint test} for {@link RunConfig} / {@link TestConfig}, {@code
 * apalache-mc} for {@link ApalacheConfig}).
 */
public sealed interface GenConfig permits RunConfig, TestConfig, ApalacheConfig {

  /** Number of traces generated when {@code maxSamples} is not given. */
  int DEFAULT_TRACES = 100;

  /** The random seed passed to the generator (as written on the command line). */
  String seed();

  /** The number of traces this configuration asks the generator for. */
  int nTraces();

  /**
   * Builds the command line that writes the traces into {@code outDir}.
   *
   * <p>The executable itself is not included; it is prepended by {@link QuintCli} or {@link
   * ApalacheCli}.
   *
   * @param outDir directory that receives the {@code *.itf.json} files
   * @return the arguments, without the executable
   */
  List<String> toCommand(Path outDir);
}
