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
package io.github.nsyee.quintconnect.runner;

import io.github.nsyee.quintconnect.itf.ItfTrace;
import java.util.Objects;

/**
 * One generated trace, ready to be replayed on demand.
 *
 * <p>Produced by {@link QuintConnect#traces}; test frameworks turn each instance into one test
 * invocation named {@link #displayName()} and call {@link #replay()} from its body.
 */
public final class TraceRun {

  private final int index;
  private final int count;
  private final String seed;
  private final ItfTrace trace;
  private final Runnable replay;

  TraceRun(int index, int count, String seed, ItfTrace trace, Runnable replay) {
    this.index = index;
    this.count = count;
    this.seed = Objects.requireNonNull(seed, "seed");
    this.trace = Objects.requireNonNull(trace, "trace");
    this.replay = Objects.requireNonNull(replay, "replay");
  }

  /** The 1-based position of this trace among the generated ones. */
  public int index() {
    return index;
  }

  /** The number of generated traces. */
  public int count() {
    return count;
  }

  /** The seed the traces were generated with. */
  public String seed() {
    return seed;
  }

  /** The parsed trace. */
  public ItfTrace trace() {
    return trace;
  }

  /** {@code [Trace i/n] seed=…}. */
  public String displayName() {
    return "[Trace " + index + "/" + count + "] seed=" + seed;
  }

  /**
   * Replays the trace through the driver, checking the state after every step.
   *
   * @throws QuintConnectException if the driver fails or the states diverge
   */
  public void replay() {
    replay.run();
  }

  @Override
  public String toString() {
    return displayName();
  }
}
