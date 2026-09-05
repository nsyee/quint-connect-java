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

import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import io.github.nsyee.quintconnect.log.Logger;
import io.github.nsyee.quintconnect.runner.QuintConnect;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * The traces of one {@link QuintRun} / {@link QuintTest} invocation, injected as a parameter of the
 * annotated method, which replays them through its driver:
 *
 * <pre>{@code
 * @QuintRun(spec = "spec/tictactoe.qnt")
 * void tictactoe(TraceReplay traces) {
 *   traces.replay(TicTacToeDriver::new);
 * }
 * }</pre>
 *
 * <p>An invocation holds a single trace, or all of them with {@code singleInvocation = true}. The
 * method must call one of the {@code replay} methods exactly once; the extension fails the
 * invocation otherwise.
 */
public final class TraceReplay {

  private final QuintConnect runner;
  private final List<ItfTrace> traces;
  private final int firstIndex;
  private final int total;
  private final String seed;
  private final Store methodStore;
  private boolean replayed;

  TraceReplay(
      QuintConnect runner,
      List<ItfTrace> traces,
      int firstIndex,
      int total,
      String seed,
      Store methodStore) {
    this.runner = Objects.requireNonNull(runner, "runner");
    this.traces = List.copyOf(traces);
    this.firstIndex = firstIndex;
    this.total = total;
    this.seed = Objects.requireNonNull(seed, "seed");
    this.methodStore = Objects.requireNonNull(methodStore, "methodStore");
  }

  /** The traces replayed by this invocation, in order. */
  public List<ItfTrace> traces() {
    return traces;
  }

  /** The 1-based index of the first trace of this invocation among all generated traces. */
  public int firstIndex() {
    return firstIndex;
  }

  /** The number of traces generated for the annotated method. */
  public int total() {
    return total;
  }

  /** The seed the traces were generated with. */
  public String seed() {
    return seed;
  }

  /** The runner performing the replay; exposes the {@link Logger} and the mapper. */
  public QuintConnect runner() {
    return runner;
  }

  /**
   * {@code [Trace i/n] seed=…} for a single trace, {@code [Traces i-j] seed=…} for a range; also
   * the display name of the invocation.
   */
  public String displayName() {
    if (traces.size() == 1) {
      return "[Trace " + firstIndex + "/" + total + "] seed=" + seed;
    }
    return "[Traces " + firstIndex + "-" + (firstIndex + traces.size() - 1) + "] seed=" + seed;
  }

  /**
   * Replays the traces through a driver that is created on the first invocation of the annotated
   * method and shared by all subsequent ones, as in the Rust crate; the {@code init} action is
   * expected to reset it.
   *
   * @param driver supplies the driver; called at most once per annotated method
   * @param <S> the specification state type
   * @throws io.github.nsyee.quintconnect.runner.QuintConnectException if the driver fails or the
   *     states diverge
   */
  public <S> void replay(Supplier<? extends Driver<S>> driver) {
    Objects.requireNonNull(driver, "driver");
    SharedDriver shared =
        methodStore.computeIfAbsent(
            QuintConnectExtension.DRIVER,
            key -> new SharedDriver(driver.get()),
            SharedDriver.class);
    replayWith(shared.driver());
  }

  /**
   * Replays the traces through the given driver instance.
   *
   * @param driver the driver
   * @param <S> the specification state type
   * @throws io.github.nsyee.quintconnect.runner.QuintConnectException if the driver fails or the
   *     states diverge
   */
  public <S> void replay(Driver<S> driver) {
    Objects.requireNonNull(driver, "driver");
    replayWith(driver);
  }

  /** Store value holding the driver shared by all invocations of one annotated method. */
  record SharedDriver(Driver<?> driver) {}

  boolean replayed() {
    return replayed;
  }

  private <S> void replayWith(Driver<S> driver) {
    if (replayed) {
      throw new IllegalStateException("replay() was already called for " + displayName());
    }
    replayed = true;
    Logger logger = runner.logger();
    try {
      int index = firstIndex;
      for (ItfTrace trace : traces) {
        runner.replayTrace(driver, trace, index++);
      }
    } catch (RuntimeException e) {
      logger.error("[FAIL] " + displayName() + " ");
      logger.error("Reproduce this error with `QUINT_SEED=" + seed + "`\n");
      throw e;
    }
    logger.success("[OK] " + displayName());
  }
}
