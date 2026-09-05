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

/**
 * Generates traces by spawning the Quint CLI.
 *
 * <p>Traces are written to a fresh {@code quint-connect-*} temporary directory which is deleted
 * when the returned {@link TraceSource} is closed. Standard output is discarded; standard error is
 * captured and reported through {@link QuintException} when Quint exits with a non-zero code.
 */
public final class QuintTraceGenerator implements TraceGenerator {

  private final QuintCli cli;
  private final Optional<Path> workingDirectory;

  private QuintTraceGenerator(QuintCli cli, Optional<Path> workingDirectory) {
    this.cli = cli;
    this.workingDirectory = workingDirectory;
  }

  /** Uses {@link QuintCli#locate()} and the current working directory. */
  public static QuintTraceGenerator create() {
    return new QuintTraceGenerator(QuintCli.locate(), Optional.empty());
  }

  /** Uses the given executable and the current working directory. */
  public static QuintTraceGenerator of(QuintCli cli) {
    return new QuintTraceGenerator(Objects.requireNonNull(cli, "cli"), Optional.empty());
  }

  /** Returns a copy that runs Quint in {@code dir} (relative spec paths resolve against it). */
  public QuintTraceGenerator inDirectory(Path dir) {
    return new QuintTraceGenerator(cli, Optional.of(dir));
  }

  /** The executable in use. */
  public QuintCli cli() {
    return cli;
  }

  @Override
  public TraceSource generate(GenConfig config) {
    Objects.requireNonNull(config, "config");
    Path tempDir = createTempDir();
    try {
      run(cli.command(config, tempDir));
      return TraceSource.ofTempDirectory(tempDir);
    } catch (RuntimeException e) {
      TraceSource.ofTempDirectory(tempDir).close();
      throw e;
    }
  }

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("quint-connect-");
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create temporary directory for traces", e);
    }
  }

  private void run(List<String> command) {
    ProcessBuilder builder =
        new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectInput(ProcessBuilder.Redirect.PIPE);
    workingDirectory.ifPresent(dir -> builder.directory(dir.toFile()));
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new QuintException(
          "Failed to execute Quint command: "
              + String.join(" ", command)
              + System.lineSeparator()
              + QuintCli.INSTALL_HINT,
          e);
    }
    String stderr;
    int exit;
    try {
      process.getOutputStream().close();
      stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      exit = process.waitFor();
    } catch (IOException e) {
      process.destroyForcibly();
      throw new QuintException("Failed to read Quint output", e);
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new QuintException("Interrupted while waiting for Quint", e);
    }
    if (exit != 0) {
      throw new QuintException("Quint returned non-zero code.", stderr, null);
    }
  }
}
