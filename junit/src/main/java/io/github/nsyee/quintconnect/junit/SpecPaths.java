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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.junit.platform.commons.JUnitException;

/**
 * Resolution of the {@code spec} attribute: a {@code classpath:} resource copied to a temporary
 * file, an absolute path, or a path relative to the working directory.
 */
final class SpecPaths {

  static final String CLASSPATH_PREFIX = "classpath:";

  private SpecPaths() {}

  /**
   * A resolved specification; {@link #close()} deletes the temporary directory holding the copy of
   * a classpath resource, if any.
   */
  record Resolved(Path path, Optional<Path> tempDir) implements AutoCloseable {
    @Override
    public void close() {
      tempDir.ifPresent(
          dir -> {
            try {
              Files.deleteIfExists(path);
              Files.deleteIfExists(dir);
            } catch (IOException e) {
              throw new UncheckedIOException("Failed to delete temporary spec: " + path, e);
            }
          });
    }
  }

  static Resolved resolve(String spec, ClassLoader loader) {
    if (spec.startsWith(CLASSPATH_PREFIX)) {
      return copyResource(spec.substring(CLASSPATH_PREFIX.length()), loader);
    }
    Path path = Path.of(spec);
    if (!Files.isRegularFile(path)) {
      throw new JUnitException(
          "Specification file not found: "
              + path
              + " (resolved against "
              + Path.of("").toAbsolutePath()
              + ")");
    }
    return new Resolved(path, Optional.empty());
  }

  private static Resolved copyResource(String name, ClassLoader loader) {
    String resource = name.startsWith("/") ? name.substring(1) : name;
    String fileName = resource.substring(resource.lastIndexOf('/') + 1);
    try (InputStream in = loader.getResourceAsStream(resource)) {
      if (in == null) {
        throw new JUnitException("Specification resource not found on the classpath: " + resource);
      }
      Path dir = Files.createTempDirectory("quint-connect-spec-");
      Path target = dir.resolve(fileName);
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      return new Resolved(target, Optional.of(dir));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to copy specification resource: " + resource, e);
    }
  }
}
