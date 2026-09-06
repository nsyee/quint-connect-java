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
 * Locates the Apalache executable.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>the {@code APALACHE_BIN} system property, then the {@code APALACHE_BIN} environment
 *       variable;
 *   <li>{@code apalache-mc.bat} on Windows, {@code apalache-mc} elsewhere, searched on {@code
 *       PATH}.
 * </ol>
 */
public final class ApalacheCli {

  /** Name of the system property / environment variable overriding the executable. */
  public static final String APALACHE_BIN = "APALACHE_BIN";

  static final String INSTALL_HINT =
      "Apalache not found. Download a release from https://github.com/apalache-mc/apalache/releases,"
          + " put its bin/ directory on PATH or point "
          + APALACHE_BIN
          + " to the executable.";

  private final String executable;

  private ApalacheCli(String executable) {
    this.executable = executable;
  }

  /** Resolves the executable from {@code APALACHE_BIN} or the default name for this OS. */
  public static ApalacheCli locate() {
    return locate(Executables.override(APALACHE_BIN), Executables.isWindows());
  }

  /** Uses the given executable as-is (name on {@code PATH} or a path). */
  public static ApalacheCli of(String executable) {
    return new ApalacheCli(executable);
  }

  static ApalacheCli locate(Optional<String> override, boolean windows) {
    return new ApalacheCli(
        override.filter(s -> !s.isBlank()).orElse(windows ? "apalache-mc.bat" : "apalache-mc"));
  }

  /** The executable name or path passed to the process builder. */
  public String executable() {
    return executable;
  }

  /**
   * Prepends the executable to the arguments produced by {@link ApalacheConfig#toCommand(Path)}.
   *
   * @param config the run configuration
   * @param outDir directory that receives the traces
   * @return the full command line
   */
  public List<String> command(ApalacheConfig config, Path outDir) {
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
    return "ApalacheCli[" + executable + "]";
  }
}
