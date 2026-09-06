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
import java.util.Optional;

/**
 * Locates the Quint executable.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>the {@code QUINT_BIN} system property, then the {@code QUINT_BIN} environment variable;
 *   <li>{@code quint.cmd} on Windows, {@code quint} elsewhere, searched on {@code PATH}.
 * </ol>
 */
public final class QuintCli {

  /** Name of the system property / environment variable overriding the executable. */
  public static final String QUINT_BIN = "QUINT_BIN";

  static final String INSTALL_HINT =
      "Quint not found. Install with `npm i -g @informalsystems/quint` or point "
          + QUINT_BIN
          + " to the executable.";

  private final String executable;

  private QuintCli(String executable) {
    this.executable = executable;
  }

  /** Resolves the executable from {@code QUINT_BIN} or the default name for this OS. */
  public static QuintCli locate() {
    return locate(Executables.override(QUINT_BIN), Executables.isWindows());
  }

  /** Uses the given executable as-is (name on {@code PATH} or a path). */
  public static QuintCli of(String executable) {
    return new QuintCli(executable);
  }

  static QuintCli locate(Optional<String> override, boolean windows) {
    return new QuintCli(override.filter(s -> !s.isBlank()).orElse(windows ? "quint.cmd" : "quint"));
  }

  static boolean isWindows(String osName) {
    return Executables.isWindows(osName);
  }

  /** The executable name or path passed to the process builder. */
  public String executable() {
    return executable;
  }

  /**
   * Prepends the executable to the arguments produced by {@link GenConfig#toCommand(Path)}.
   *
   * @param config the run configuration
   * @param outDir directory that receives the traces
   * @return the full command line
   * @throws IllegalArgumentException if {@code config} is an {@link ApalacheConfig}
   */
  public List<String> command(GenConfig config, Path outDir) {
    if (config instanceof ApalacheConfig) {
      throw new IllegalArgumentException(
          "ApalacheConfig must be run with ApalacheTraceGenerator, not the Quint CLI");
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(executable);
    cmd.addAll(config.toCommand(outDir));
    return cmd;
  }

  /**
   * Checks whether the executable can be found, either as a path or on {@code PATH}.
   *
   * @return {@code true} if the executable exists
   */
  public boolean isAvailable() {
    return resolve().isPresent();
  }

  /** Resolves the executable to an existing file, if it can be found. */
  public Optional<Path> resolve() {
    return resolve(System.getenv("PATH"), System.getenv("PATHEXT"));
  }

  Optional<Path> resolve(String pathEnv, String pathExt) {
    return Executables.resolve(executable, pathEnv, pathExt);
  }

  @Override
  public String toString() {
    return "QuintCli[" + executable + "]";
  }
}
