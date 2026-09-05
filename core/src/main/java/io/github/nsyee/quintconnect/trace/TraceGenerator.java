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

/**
 * Produces ITF traces for a {@link GenConfig}.
 *
 * <p>Implementations: {@link QuintTraceGenerator} (spawns the Quint CLI) and {@link
 * FileTraceGenerator} (replays existing {@code *.itf.json} files).
 */
@FunctionalInterface
public interface TraceGenerator {

  /**
   * Generates the traces described by {@code config}.
   *
   * <p>The returned source must be closed by the caller; closing it releases any temporary files.
   *
   * @param config what to generate
   * @return the traces, lazily parsed
   * @throws QuintException if the generator fails before any trace is produced
   */
  TraceSource generate(GenConfig config);
}
