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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Generates traces from a TLA+ specification by spawning Apalache ({@code apalache-mc}).
 *
 * <p>Apalache writes its run directory into a fresh {@code quint-connect-apalache-*} temporary
 * directory which is deleted when the returned {@link TraceSource} is closed. Only the numbered
 * traces ({@code example<i>.itf.json} from {@code simulate}, {@code violation<i>.itf.json} from
 * {@code check}) are exposed; Apalache's unnumbered copy of the last trace is ignored.
 *
 * <p>Apalache exits with code 12 when it found an invariant violation; for this generator that is a
 * success (the violations are the traces). Any other non-zero exit code raises a {@link
 * QuintException} carrying Apalache's console output.
 */
public final class ApalacheTraceGenerator implements TraceGenerator {

  /** Exit code Apalache uses to report a counterexample. */
  static final int EXIT_VIOLATION = 12;

  static final Pattern TRACE_FILES = Pattern.compile("^(example|violation)\\d+\\.itf\\.json$");

  private final ApalacheCli cli;
  private final Optional<Path> workingDirectory;

  private ApalacheTraceGenerator(ApalacheCli cli, Optional<Path> workingDirectory) {
    this.cli = cli;
    this.workingDirectory = workingDirectory;
  }

  /** Uses {@link ApalacheCli#locate()} and the current working directory. */
  public static ApalacheTraceGenerator create() {
    return new ApalacheTraceGenerator(ApalacheCli.locate(), Optional.empty());
  }

  /** Uses the given executable and the current working directory. */
  public static ApalacheTraceGenerator of(ApalacheCli cli) {
    return new ApalacheTraceGenerator(Objects.requireNonNull(cli, "cli"), Optional.empty());
  }

  /** Returns a copy that runs Apalache in {@code dir} (relative spec paths resolve against it). */
  public ApalacheTraceGenerator inDirectory(Path dir) {
    return new ApalacheTraceGenerator(cli, Optional.of(dir));
  }

  /** The executable in use. */
  public ApalacheCli cli() {
    return cli;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code config} is not an {@link ApalacheConfig}
   */
  @Override
  public TraceSource generate(GenConfig config) {
    Objects.requireNonNull(config, "config");
    if (!(config instanceof ApalacheConfig apalache)) {
      throw new IllegalArgumentException(
          "ApalacheTraceGenerator requires an ApalacheConfig, got "
              + config.getClass().getSimpleName());
    }
    Path tempDir = createTempDir();
    try {
      run(cli.command(apalache, tempDir));
      return TraceSource.ofTempDirectory(tempDir, TRACE_FILES.asMatchPredicate());
    } catch (RuntimeException e) {
      TraceSource.ofTempDirectory(tempDir).close();
      throw e;
    }
  }

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("quint-connect-apalache-");
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create temporary directory for traces", e);
    }
  }

  private void run(List<String> command) {
    ProcessBuilder builder =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.PIPE);
    workingDirectory.ifPresent(dir -> builder.directory(dir.toFile()));
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new QuintException(
          "Failed to execute Apalache command: "
              + String.join(" ", command)
              + System.lineSeparator()
              + ApalacheCli.INSTALL_HINT,
          e);
    }
    String output;
    int exit;
    try {
      process.getOutputStream().close();
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      exit = process.waitFor();
    } catch (IOException e) {
      process.destroyForcibly();
      throw new QuintException("Failed to read Apalache output", e);
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new QuintException("Interrupted while waiting for Apalache", e);
    }
    if (exit != 0 && exit != EXIT_VIOLATION) {
      throw new QuintException("Apalache returned non-zero code.", output, null);
    }
  }
}
