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

import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A set of ITF trace files, parsed lazily and in a stable order.
 *
 * <p>Files are ordered by the numeric {@code {seq}} component of their name ({@code
 * run_12.itf.json} after {@code run_2.itf.json}); files without a number sort after those with one,
 * by name. When constructed with a temporary directory, {@link #close()} deletes it.
 */
public final class TraceSource implements Closeable {

  private static final Pattern SEQ = Pattern.compile("(\\d+)\\.itf\\.json$");

  private final List<Path> files;
  private final Optional<Path> tempDir;

  private TraceSource(List<Path> files, Optional<Path> tempDir) {
    this.files = List.copyOf(files);
    this.tempDir = tempDir;
  }

  /** A source over the given files; nothing is deleted on close. */
  public static TraceSource ofFiles(List<Path> files) {
    Objects.requireNonNull(files, "files");
    List<Path> sorted = new ArrayList<>(files);
    sorted.sort(ORDER);
    return new TraceSource(sorted, Optional.empty());
  }

  /** A source over the {@code *.itf.json} files in {@code dir}; nothing is deleted on close. */
  public static TraceSource ofDirectory(Path dir) {
    return new TraceSource(listTraceFiles(dir), Optional.empty());
  }

  /**
   * A source over the {@code *.itf.json} files in a temporary directory that is deleted, with its
   * contents, on {@link #close()}.
   */
  public static TraceSource ofTempDirectory(Path tempDir) {
    return new TraceSource(listTraceFiles(tempDir), Optional.of(tempDir));
  }

  /** The trace files, in replay order. */
  public List<Path> files() {
    return files;
  }

  /** The number of trace files. */
  public int size() {
    return files.size();
  }

  /**
   * Parses the traces one by one.
   *
   * @throws io.github.nsyee.quintconnect.itf.ItfParseException if a file is not a valid trace
   * @throws UncheckedIOException if a file cannot be read
   */
  public Stream<ItfTrace> traces() {
    return files.stream().map(ItfParser::parse);
  }

  /** Parses all traces eagerly. */
  public List<ItfTrace> toList() {
    return traces().toList();
  }

  @Override
  public void close() {
    tempDir.ifPresent(TraceSource::deleteRecursively);
  }

  static List<Path> listTraceFiles(Path dir) {
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          .filter(p -> p.getFileName().toString().endsWith(".itf.json"))
          .filter(Files::isRegularFile)
          .sorted(ORDER)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list trace files at: " + dir, e);
    }
  }

  private static final Comparator<Path> ORDER =
      Comparator.comparingLong(TraceSource::seq).thenComparing(p -> p.getFileName().toString());

  private static long seq(Path path) {
    Matcher m = SEQ.matcher(path.getFileName().toString());
    if (m.find()) {
      try {
        return Long.parseLong(m.group(1));
      } catch (NumberFormatException e) {
        return Long.MAX_VALUE;
      }
    }
    return Long.MAX_VALUE;
  }

  private static void deleteRecursively(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete temporary directory: " + dir, e);
    }
  }
}
