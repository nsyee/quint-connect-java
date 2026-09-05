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

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    return locate(
        Optional.ofNullable(System.getProperty(QUINT_BIN))
            .or(() -> Optional.ofNullable(System.getenv(QUINT_BIN))),
        isWindows(System.getProperty("os.name", "")));
  }

  /** Uses the given executable as-is (name on {@code PATH} or a path). */
  public static QuintCli of(String executable) {
    return new QuintCli(executable);
  }

  static QuintCli locate(Optional<String> override, boolean windows) {
    return new QuintCli(override.filter(s -> !s.isBlank()).orElse(windows ? "quint.cmd" : "quint"));
  }

  static boolean isWindows(String osName) {
    return osName.toLowerCase(Locale.ROOT).contains("win");
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
   */
  public List<String> command(GenConfig config, Path outDir) {
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
    Path candidate;
    try {
      candidate = Path.of(executable);
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
    if (candidate.getNameCount() > 1 || candidate.isAbsolute()) {
      return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
    if (pathEnv == null) {
      return Optional.empty();
    }
    List<String> suffixes = new ArrayList<>();
    suffixes.add("");
    if (pathExt != null) {
      for (String ext : pathExt.split(java.io.File.pathSeparator, -1)) {
        if (!ext.isBlank()) {
          suffixes.add(ext);
        }
      }
    }
    for (String dir : pathEnv.split(java.io.File.pathSeparator, -1)) {
      if (dir.isBlank()) {
        continue;
      }
      for (String suffix : suffixes) {
        Path file;
        try {
          file = Path.of(dir, executable + suffix);
        } catch (InvalidPathException e) {
          continue;
        }
        if (Files.isRegularFile(file)) {
          return Optional.of(file);
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public String toString() {
    return "QuintCli[" + executable + "]";
  }
}
