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
import java.util.Objects;

/**
 * A {@link TraceGenerator} that ignores the configuration and replays pre-generated {@code
 * *.itf.json} files.
 *
 * <p>Useful in tests and for users who generate traces out of band (e.g. once in CI and commit
 * them).
 */
public final class FileTraceGenerator implements TraceGenerator {

  private final List<Path> files;

  private FileTraceGenerator(List<Path> files) {
    this.files = List.copyOf(files);
  }

  /** Replays the given files, in {@link TraceSource} order. */
  public static FileTraceGenerator of(Path... files) {
    return new FileTraceGenerator(List.of(files));
  }

  /** Replays the given files, in {@link TraceSource} order. */
  public static FileTraceGenerator of(List<Path> files) {
    return new FileTraceGenerator(Objects.requireNonNull(files, "files"));
  }

  /** Replays every {@code *.itf.json} file found directly in {@code dir}. */
  public static FileTraceGenerator ofDirectory(Path dir) {
    return new FileTraceGenerator(TraceSource.listTraceFiles(dir));
  }

  @Override
  public TraceSource generate(GenConfig config) {
    Objects.requireNonNull(config, "config");
    return TraceSource.ofFiles(files);
  }
}
