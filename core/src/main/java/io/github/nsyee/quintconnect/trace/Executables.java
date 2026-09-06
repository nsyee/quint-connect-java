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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Executable lookup shared by {@link QuintCli} and {@link ApalacheCli}. */
final class Executables {

  private Executables() {}

  static Optional<String> override(String name) {
    return Optional.ofNullable(System.getProperty(name))
        .or(() -> Optional.ofNullable(System.getenv(name)))
        .filter(s -> !s.isBlank());
  }

  static boolean isWindows(String osName) {
    return osName.toLowerCase(Locale.ROOT).contains("win");
  }

  static boolean isWindows() {
    return isWindows(System.getProperty("os.name", ""));
  }

  /**
   * Resolves {@code executable} to an existing file: as a path if it has more than one name
   * component, otherwise by searching {@code pathEnv} (honouring {@code pathExt} suffixes).
   */
  static Optional<Path> resolve(String executable, String pathEnv, String pathExt) {
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
      for (String ext : pathExt.split(File.pathSeparator, -1)) {
        if (!ext.isBlank()) {
          suffixes.add(ext);
        }
      }
    }
    for (String dir : pathEnv.split(File.pathSeparator, -1)) {
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
}
