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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Thrown by {@link QuintConnect} when a run fails: no traces were generated, an anonymous action
 * was found, the driver threw, the specification state could not be mapped, or the states diverged.
 *
 * <p>The exception carries the position at which the failure happened — trace index (1-based, as
 * printed in {@code [Trace n]}), step index (0-based, as in {@code [Step n]}) and action name — and
 * the {@link StateDiff} for divergences. {@link #getMessage()} includes all of that so test
 * frameworks show it in the failure report.
 */
public class QuintConnectException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String summary;
  private final transient Context context;
  private final transient Optional<StateDiff> diff;

  /** Where in the replay a failure happened. Missing parts are simply not reported. */
  public record Context(OptionalInt trace, OptionalInt step, Optional<String> action) {

    /** No position information. */
    public static final Context NONE =
        new Context(OptionalInt.empty(), OptionalInt.empty(), Optional.empty());

    /** Creates a context, rejecting {@code null}s. */
    public Context {
      Objects.requireNonNull(trace, "trace");
      Objects.requireNonNull(step, "step");
      Objects.requireNonNull(action, "action");
    }

    /** A context pointing at a trace only. */
    public static Context ofTrace(int trace) {
      return new Context(OptionalInt.of(trace), OptionalInt.empty(), Optional.empty());
    }

    /** Returns a copy pointing at {@code step} within the current trace. */
    public Context atStep(int step) {
      return new Context(trace, OptionalInt.of(step), action);
    }

    /** Returns a copy naming the current action. */
    public Context withAction(String action) {
      return new Context(trace, step, Optional.of(action));
    }

    /** Renders the known parts, e.g. {@code trace 1, step 3, action `MoveX`}, or {@code ""}. */
    public String describe() {
      StringBuilder sb = new StringBuilder();
      trace.ifPresent(t -> sb.append("trace ").append(t));
      step.ifPresent(s -> sb.append(sb.isEmpty() ? "" : ", ").append("step ").append(s));
      action.ifPresent(
          a -> sb.append(sb.isEmpty() ? "" : ", ").append("action `").append(a).append('`'));
      return sb.toString();
    }
  }

  /** Creates an exception without position information. */
  public QuintConnectException(String message) {
    this(message, Context.NONE, Optional.empty(), null);
  }

  /** Creates an exception with position information. */
  public QuintConnectException(String message, Context context) {
    this(message, context, Optional.empty(), null);
  }

  /** Creates an exception with position information and a cause. */
  public QuintConnectException(String message, Context context, Throwable cause) {
    this(message, context, Optional.empty(), cause);
  }

  /** Creates an exception for a state divergence. */
  public QuintConnectException(String message, Context context, StateDiff diff) {
    this(message, context, Optional.of(diff), null);
  }

  private QuintConnectException(
      String message, Context context, Optional<StateDiff> diff, Throwable cause) {
    super(render(message, context, diff), cause);
    this.summary = Objects.requireNonNull(message, "message");
    this.context = Objects.requireNonNull(context, "context");
    this.diff = Objects.requireNonNull(diff, "diff");
  }

  /** The message without position and diff, e.g. {@code State invariant failed}. */
  public String summary() {
    return summary;
  }

  /** Where the failure happened. */
  public Context context() {
    return context;
  }

  /** The trace index (1-based), if known. */
  public OptionalInt traceIndex() {
    return context.trace();
  }

  /** The step index (0-based), if known. */
  public OptionalInt stepIndex() {
    return context.step();
  }

  /** The action being replayed, if known. */
  public Optional<String> action() {
    return context.action();
  }

  /** The state diff, for divergences. */
  public Optional<StateDiff> diff() {
    return diff;
  }

  private static String render(String message, Context context, Optional<StateDiff> diff) {
    StringBuilder sb = new StringBuilder(message);
    String where = context.describe();
    if (!where.isEmpty()) {
      sb.append(" (").append(where).append(')');
    }
    diff.ifPresent(d -> sb.append('\n').append(d.unified()));
    return sb.toString();
  }
}
