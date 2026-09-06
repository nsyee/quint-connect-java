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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Configuration for generating ITF traces from a TLA+ specification with <a
 * href="https://apalache-mc.org">Apalache</a>.
 *
 * <p>Two modes are supported:
 *
 * <ul>
 *   <li>{@link ApalacheMode#SIMULATE}: {@code apalache-mc simulate --output-traces --max-run=n}
 *       writes one random trace per run ({@code example1.itf.json}, …). This is the closest
 *       equivalent of {@code quint run}.
 *   <li>{@link ApalacheMode#CHECK}: {@code apalache-mc check --inv=… --max-error=n --view=…} writes
 *       one trace per invariant violation ({@code violation1.itf.json}, …). Useful to drive the
 *       implementation into specific situations by negating the condition of interest.
 * </ul>
 *
 * <p>Apalache has no {@code --mbt} mode, so the specification itself must record the action taken
 * in a variable holding a sum-type-shaped record ({@code [tag |-> "Deposit", value |-> [amount |->
 * 3]]}); point {@link io.github.nsyee.quintconnect.driver.DriverConfig#nondetPath} at that
 * variable.
 *
 * <p>The seed is passed to the SMT solver as {@code smt.randomSeed}. Apalache's simulation picks
 * transitions with its own, unseeded random generator, so — unlike Quint — traces are <em>not</em>
 * reproducible from the seed alone.
 *
 * @param spec path of the TLA+ specification
 * @param mode {@code simulate} or {@code check}
 * @param init init predicate, if not {@code Init}
 * @param next transition predicate, if not {@code Next}
 * @param cinit constant initialisation predicate, if any
 * @param invariants invariants to check ({@code --inv}), possibly empty
 * @param length maximal number of {@code Next} steps ({@code --length}, Apalache default 10)
 * @param maxRuns number of simulation runs ({@code --max-run}); {@link ApalacheMode#SIMULATE} only
 * @param maxErrors number of counterexamples ({@code --max-error}); {@link ApalacheMode#CHECK} only
 * @param view state view operator required by Apalache when {@code maxErrors > 1} ({@code --view})
 * @param seed random seed, a decimal or {@code 0x}-prefixed integer as produced by {@link Seeds}
 */
public record ApalacheConfig(
    Path spec,
    ApalacheMode mode,
    Optional<String> init,
    Optional<String> next,
    Optional<String> cinit,
    List<String> invariants,
    OptionalInt length,
    OptionalInt maxRuns,
    OptionalInt maxErrors,
    Optional<String> view,
    String seed)
    implements GenConfig {

  /** Name of the sub-directory (inside the trace directory) receiving Apalache's own output. */
  static final String OUT_DIR = "_apalache-out";

  /** Validates that no component is {@code null} and that the seed is an integer. */
  public ApalacheConfig {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(init, "init");
    Objects.requireNonNull(next, "next");
    Objects.requireNonNull(cinit, "cinit");
    invariants = List.copyOf(Objects.requireNonNull(invariants, "invariants"));
    Objects.requireNonNull(length, "length");
    Objects.requireNonNull(maxRuns, "maxRuns");
    Objects.requireNonNull(maxErrors, "maxErrors");
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(seed, "seed");
    smtSeed(seed);
  }

  /** A {@link ApalacheMode#SIMULATE} configuration with only the spec and seed set. */
  public static ApalacheConfig simulate(Path spec, String seed) {
    return of(spec, ApalacheMode.SIMULATE, seed);
  }

  /** A {@link ApalacheMode#CHECK} configuration with only the spec and seed set. */
  public static ApalacheConfig check(Path spec, String seed) {
    return of(spec, ApalacheMode.CHECK, seed);
  }

  private static ApalacheConfig of(Path spec, ApalacheMode mode, String seed) {
    return new ApalacheConfig(
        spec,
        mode,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        List.of(),
        OptionalInt.empty(),
        OptionalInt.empty(),
        OptionalInt.empty(),
        Optional.empty(),
        seed);
  }

  /** Returns a copy with the given init predicate. */
  public ApalacheConfig withInit(String init) {
    return new ApalacheConfig(
        spec,
        mode,
        Optional.of(init),
        next,
        cinit,
        invariants,
        length,
        maxRuns,
        maxErrors,
        view,
        seed);
  }

  /** Returns a copy with the given transition predicate. */
  public ApalacheConfig withNext(String next) {
    return new ApalacheConfig(
        spec,
        mode,
        init,
        Optional.of(next),
        cinit,
        invariants,
        length,
        maxRuns,
        maxErrors,
        view,
        seed);
  }

  /** Returns a copy with the given constant initialisation predicate. */
  public ApalacheConfig withCinit(String cinit) {
    return new ApalacheConfig(
        spec,
        mode,
        init,
        next,
        Optional.of(cinit),
        invariants,
        length,
        maxRuns,
        maxErrors,
        view,
        seed);
  }

  /** Returns a copy with the given invariants. */
  public ApalacheConfig withInvariants(String... invariants) {
    return new ApalacheConfig(
        spec, mode, init, next, cinit, List.of(invariants), length, maxRuns, maxErrors, view, seed);
  }

  /** Returns a copy with the given maximal number of steps. */
  public ApalacheConfig withLength(int length) {
    return new ApalacheConfig(
        spec,
        mode,
        init,
        next,
        cinit,
        invariants,
        OptionalInt.of(length),
        maxRuns,
        maxErrors,
        view,
        seed);
  }

  /** Returns a copy with the given number of simulation runs. */
  public ApalacheConfig withMaxRuns(int maxRuns) {
    return new ApalacheConfig(
        spec,
        mode,
        init,
        next,
        cinit,
        invariants,
        length,
        OptionalInt.of(maxRuns),
        maxErrors,
        view,
        seed);
  }

  /** Returns a copy with the given number of counterexamples and the view partitioning them. */
  public ApalacheConfig withMaxErrors(int maxErrors, String view) {
    return new ApalacheConfig(
        spec,
        mode,
        init,
        next,
        cinit,
        invariants,
        length,
        maxRuns,
        OptionalInt.of(maxErrors),
        Optional.of(view),
        seed);
  }

  /** Returns a copy with the given seed. */
  public ApalacheConfig withSeed(String seed) {
    return new ApalacheConfig(
        spec, mode, init, next, cinit, invariants, length, maxRuns, maxErrors, view, seed);
  }

  /**
   * The number of traces requested: {@code maxRuns} (default {@link #DEFAULT_TRACES}) when
   * simulating, {@code maxErrors} (default 1) when checking.
   */
  @Override
  public int nTraces() {
    return switch (mode) {
      case SIMULATE -> maxRuns.orElse(DEFAULT_TRACES);
      case CHECK -> maxErrors.orElse(1);
    };
  }

  /**
   * The seed as passed to the SMT solver: the integer value of {@link #seed()} reduced to a
   * non-negative 31-bit number, which is what Z3 and cvc5 accept.
   */
  public long smtSeed() {
    return smtSeed(seed);
  }

  private static long smtSeed(String seed) {
    try {
      return Math.floorMod(Long.decode(seed.trim()), 1L << 31);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Apalache seed must be a decimal or 0x-prefixed integer, got: " + seed, e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Apalache is told to write its run directory straight into {@code outDir} ({@code --run-dir})
   * and its bookkeeping into {@code outDir/_apalache-out}; the ITF traces thus land in {@code
   * outDir} as {@code example<i>.itf.json} or {@code violation<i>.itf.json}.
   */
  @Override
  public List<String> toCommand(Path outDir) {
    List<String> cmd = new ArrayList<>();
    cmd.add("--run-dir=" + outDir);
    cmd.add("--out-dir=" + outDir.resolve(OUT_DIR));
    cmd.add(mode.command());
    if (mode == ApalacheMode.SIMULATE) {
      cmd.add("--output-traces");
    }
    cmd.add("--tuning-options=smt.randomSeed=" + smtSeed());
    init.ifPresent(v -> cmd.add("--init=" + v));
    next.ifPresent(v -> cmd.add("--next=" + v));
    cinit.ifPresent(v -> cmd.add("--cinit=" + v));
    if (!invariants.isEmpty()) {
      cmd.add("--inv=" + String.join(",", invariants));
    }
    length.ifPresent(v -> cmd.add("--length=" + v));
    if (mode == ApalacheMode.SIMULATE) {
      cmd.add("--max-run=" + nTraces());
    } else {
      maxErrors.ifPresent(v -> cmd.add("--max-error=" + v));
    }
    view.ifPresent(v -> cmd.add("--view=" + v));
    cmd.add(spec.toString());
    return cmd;
  }
}
