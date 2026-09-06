# quint-connect-java — Design

A Java port of [quint-connect](https://github.com/informalsystems/quint-connect)
(Rust, v0.1.2): a model-based testing (MBT) library that generates execution
traces from a [Quint](https://quint-lang.org/) specification and replays them
against an implementation, checking after every step that the implementation
state matches the specification state.

This document describes the architecture and public API of the Java port. The
delivery plan is in [work-plan.md](work-plan.md).

## 1. Goals and non-goals

Goals

- Feature parity with quint-connect 0.1.2:
  - `quint run --mbt` (simulation) and `quint test` (named Quint run) trace
    generation, with `main`, `init`, `step`, `max_samples`, `max_steps`, `seed`.
  - Replay of ITF traces through a user-supplied driver.
  - Extraction of `mbt::actionTaken` / `mbt::nondetPicks`, or of a user-tracked
    sum type at a configurable path (Choreo-style specs).
  - State abstraction function + deep equality check with a readable diff.
  - `QUINT_SEED` reproduction and `QUINT_VERBOSE` (0/1/2) logging.
- Idiomatic Java: `record`, `sealed interface`, pattern matching for `switch`,
  JUnit 5 integration instead of procedural macros.
- Zero code generation. Everything works with plain Java 21 and annotations
  processed at runtime.
- Keep the door open for a second trace source (Apalache / TLA+, see §9)
  without changing the user-facing API.

Non-goals (for the first release)

- A Quint interpreter or parser in Java. Quint stays an external CLI.
- Property-based / stateful testing beyond trace replay (no shrinking, etc.).
- Kotlin/Scala-specific APIs (they can consume the Java API directly).

## 2. Reference: how quint-connect (Rust) works

```
 #[quint_run]/#[quint_test]        RunConfig / TestConfig
   (proc macro)   ───────────►  runner::run_test(driver, config)
                                   │
                                   ├─ generate_traces()  ── spawns `quint run|test … --out-itf tmp/{seq}.itf.json [--mbt]`
                                   │                        parses each *.itf.json with the `itf` crate
                                   │
                                   └─ replay_traces()
                                        for each trace, for each state:
                                          Step::new(state, Driver::config())   ── pulls action / nondet picks / state subtree
                                          driver.step(&step)                  ── user code (switch! macro dispatch)
                                          check_state()                       ── State::from_spec(itf) == State::from_driver(driver)
                                                                                 else print unified diff + fail
```

Key files and what they become in Java:

| Rust (connect/src)                     | Responsibility                                          | Java package (proposed)                    |
| -------------------------------------- | ------------------------------------------------------- | ------------------------------------------ |
| `trace/generator/{run,test,utils}.rs`  | Build & spawn the `quint` command, temp dir             | `io.github.nsyee.quintconnect.trace`       |
| `trace/iter.rs`, `itf` crate           | Read/parse `*.itf.json`                                 | `…quintconnect.itf`                        |
| `driver/step.rs`, `driver/nondet.rs`   | Extract action / picks / state from an ITF state record | `…quintconnect.driver`                     |
| `driver/state.rs`, `driver/mod.rs`     | `Driver`, `State`, `Config`                             | `…quintconnect.driver` (public API)        |
| `value/{display,option}.rs`            | Quint-like pretty printing, `Some/None` unwrapping      | `…quintconnect.itf`                        |
| `runner/mod.rs`, `runner/seed.rs`      | Orchestration, state comparison, seed                   | `…quintconnect.runner`                     |
| `logger/*`                             | Colored, indented, verbosity-gated logging              | `…quintconnect.log`                        |
| `connect-macros` (`quint_run`, `quint_test`, `switch!`) | Test declaration & action dispatch     | `…quintconnect.junit` + `Step` fluent API  |

## 3. High-level architecture (Java)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ quint-connect-junit (JUnit 5 extension)                                  │
│   @QuintRun / @QuintTest  ──►  QuintConnectExtension                     │
│                                (TestTemplateInvocationContextProvider)   │
└───────────────┬──────────────────────────────────────────────────────────┘
                │ uses
┌───────────────▼──────────────────────────────────────────────────────────┐
│ quint-connect-core (framework-agnostic)                                  │
│                                                                          │
│  runner   QuintConnect.run(driverFactory, RunConfig|TestConfig)          │
│             ├─ trace   TraceGenerator (QuintCli) ──► Stream<ItfTrace>    │
│             ├─ driver  Step / NondetPicks / DriverConfig                 │
│             ├─ itf     ItfValue (sealed) + ItfMapper (ITF → Java types)  │
│             └─ report  StateDiff, Logger (QUINT_VERBOSE), seed hint      │
└──────────────────────────────────────────────────────────────────────────┘
                │ spawns
        ┌───────▼────────┐
        │  quint (CLI)   │  quint run … --mbt --out-itf …  /  quint test … --out-itf …
        └────────────────┘
```

Two Gradle modules keep JUnit out of the core so the runner can be used from
other frameworks (TestNG, Kotest, plain `main`):

- `quint-connect-core` — depends only on Jackson (`jackson-databind`) and
  `java-diff-utils`.
- `quint-connect-junit` — depends on `core` and `junit-jupiter-api`.
- `examples` — tictactoe and two-phase-commit ports (not published).

## 4. Toolchain

| Item             | Choice                                      | Rationale                                                                |
| ---------------- | ------------------------------------------- | ------------------------------------------------------------------------ |
| Java             | 21 (LTS), toolchain pinned via Gradle       | records, sealed types, pattern matching `switch`, `SequencedMap`         |
| Build            | Gradle (Kotlin DSL) + wrapper, `foojay` toolchain resolver | consistent with other repos in this org; auto-provisions JDK |
| Test framework   | JUnit 5 (Jupiter)                           | `@TestTemplate` extension model = closest analogue to `#[quint_test]`    |
| JSON             | Jackson `jackson-databind`                  | `JsonNode` tree for raw ITF; databind for user types                     |
| Diff             | `io.github.java-diff-utils:java-diff-utils` | unified diff, like the `similar` crate                                   |
| Formatting/lint  | Spotless (google-java-format) + Error Prone | keep the code base consistent from day one                               |
| CI               | GitHub Actions: `npm i -g @informalsystems/quint`, `./gradlew check` | integration tests need the real CLI                 |
| License          | Apache-2.0 (same as upstream; retain upstream NOTICE) | derivative work                                                |

## 5. Module `core` — detailed design

### 5.1 ITF model (`itf` package)

ITF (Informal Trace Format) is the JSON format emitted by both Quint and
Apalache. The Rust port relies on the `itf` crate; there is no Java
equivalent, so the port owns a small model:

```java
public sealed interface ItfValue permits
    ItfValue.Bool, ItfValue.Int, ItfValue.Str, ItfValue.List, ItfValue.Tuple,
    ItfValue.Set, ItfValue.Map, ItfValue.Record, ItfValue.Unserializable {

  record Bool(boolean value)                       implements ItfValue {}
  record Int(BigInteger value)                     implements ItfValue {}  // JSON number or {"#bigint": "…"}
  record Str(String value)                         implements ItfValue {}
  record List(java.util.List<ItfValue> items)      implements ItfValue {}  // JSON array
  record Tuple(java.util.List<ItfValue> items)     implements ItfValue {}  // {"#tup": […]}
  record Set(java.util.List<ItfValue> items)       implements ItfValue {}  // {"#set": […]}  (order preserved for display)
  record Map(java.util.List<Entry> entries)        implements ItfValue {}  // {"#map": [[k, v], …]}
  record Record(SequencedMap<String, ItfValue> fields) implements ItfValue {} // plain JSON object
  record Unserializable(String repr)               implements ItfValue {}  // {"#unserializable": "…"}
}

public record ItfTrace(ItfMeta meta, List<String> vars, List<ItfState> states, OptionalInt loop) {}
public record ItfState(int index, ItfValue.Record value) {}
```

- `ItfParser.parse(Path)` / `parse(InputStream)` map a Jackson `JsonNode` into
  the tree above. Sum-type variants stay as `Record{tag, value}` exactly like in
  Rust; `ItfValues.asOption(value)` implements the `Some/None` unwrapping of
  `value/option.rs`.
- `ItfDisplay.format(ItfValue)` reproduces the Quint-like rendering of
  `value/display.rs` (`Set(1, 2)`, `Map(1 -> "a")`, `Occupied(X)`, `{ a: 1 }`)
  so log output and diffs look the same as in the Rust version.
- Equality of `ItfValue` is structural (records give this for free).
  `Set`/`Map` equality must be order-insensitive; implement `equals`/`hashCode`
  explicitly for those two records.

### 5.2 ITF → Java type mapping (`ItfMapper`)

This replaces `serde::Deserialize`. Users declare the specification state and
nondet-pick types as plain Java types; the mapper converts an `ItfValue` into
them. Implemented as a Jackson `ObjectMapper` pre-configured with an
`ItfModule`, fed by a `JsonNode` derived from the `ItfValue`:

| Quint / ITF                             | Java target                                      | Notes                                                                |
| --------------------------------------- | ------------------------------------------------ | -------------------------------------------------------------------- |
| `int`                                   | `int`, `long`, `BigInteger`                      | `#bigint` handled; overflow is an error                              |
| `bool`, `str`                           | `boolean`, `String`                              |                                                                      |
| record `{ a: 1 }`                       | Java `record` (or POJO)                          | field names must match; `@JsonProperty` for renames (`nextTurn`)     |
| `Set[T]` (`#set`)                       | `Set<T>` (materialised as `LinkedHashSet`/`TreeSet`) |                                                                  |
| `List[T]`                               | `List<T>`                                        |                                                                      |
| `T1 -> T2` (`#map`)                     | `Map<K, V>` with **non-string keys**             | custom deserializer: `[[k, v], …]` pairs → keys deserialised as `K`  |
| tuple `#tup`                            | Java `record` with N components, or `List<Object>` | positional mapping                                                 |
| sum type `{ tag, value }`               | `sealed interface` + `record` variants           | see below                                                            |
| `Option[T]` (`Some(x)` / `None`)        | `Optional<T>` (or nullable field)                | `ItfModule` deserializer                                             |

Sum types. Quint serialises `type Square = Occupied(Player) | Empty` as
`{ "tag": "Occupied", "value": … }`. The `ItfModule` registers a generic
deserializer for **any sealed interface**:

1. pick the permitted subtype whose simple name equals `tag`
   (override with `@ItfVariant("Name")`);
2. if the variant record has 0 components → ignore `value`;
   1 component → deserialise `value` into it;
   N components → `value` must be a tuple, deserialised positionally.

```java
sealed interface Square permits Occupied, Empty {}
record Occupied(Player player) implements Square {}
record Empty() implements Square {}

enum Player { X, O }        // tag-only sum types may also map to a Java enum
```

No `@JsonTypeInfo`/`@JsonSubTypes` boilerplate is needed, but users who prefer
standard Jackson annotations can still use them (the module only kicks in when
no explicit type info is present).

### 5.3 Driver API (`driver` package)

```java
/** Connects an implementation to a Quint specification. S is the abstract (spec) state type. */
public interface Driver<S> {
  /** Executes one trace step against the implementation. */
  void step(Step step) throws Exception;

  /** Abstraction function: implementation state → specification state. */
  S state();

  /** Type token used to deserialise the specification state. */
  Class<S> stateType();                 // or TypeReference<S> for generics

  /** Where to find state / nondet picks in the ITF state record (defaults: top level / mbt vars). */
  default DriverConfig config() { return DriverConfig.DEFAULT; }
}

public record DriverConfig(List<String> statePath, List<String> nondetPath) {
  public static final DriverConfig DEFAULT = new DriverConfig(List.of(), List.of());
  public static DriverConfig statePath(String... segments) { … }
  public DriverConfig nondetPath(String... segments) { … }
}
```

- `Driver<Void>` (or `stateType() == Void.class`) disables state checking, like
  `type State = ()` in Rust.
- `Driver` instances are created per test run through a `Supplier<Driver<?>>`
  so each trace can start from a fresh instance if desired (the Rust version
  reuses one driver across all traces; `init` must reset it — we keep that
  semantics but also offer `freshDriverPerTrace = true`).

`Step` is what the user pattern-matches on:

```java
public final class Step {
  public String action();                                   // "" for anonymous actions (rejected by the runner)
  public NondetPicks picks();
  public <T> T pick(String name, Class<T> type);            // required; throws if missing
  public <T> Optional<T> optionalPick(String name, Class<T> type);
  public <T> T pick(String name, TypeReference<T> type);    // generics
  ItfValue specState();                                     // package-private; used by the runner
}
```

Action dispatch — the replacement for `switch!`. Plain Java 21 is already
close to the macro:

```java
@Override
public void step(Step step) {
  switch (step.action()) {
    case "init"  -> game = new TicTacToe();
    case "MoveX" -> {
      var pos = step.optionalPick("corner", Position.class)
                    .or(() -> step.optionalPick("coordinate", Position.class))
                    .orElse(new Position(1, 1));
      game.moveTo(toGamePos(pos), Player.X);
    }
    case "MoveO" -> game.moveTo(toGamePos(step.pick("coordinate", Position.class)), Player.O);
    case "stuttered" -> {}
    default -> throw Step.unimplemented(step);   // "Unimplemented action `…`"
  }
}
```

An optional fluent helper gives the same "unknown action fails by default"
guarantee without the `default` branch:

```java
Actions.on(step)
  .action("init",  () -> game = new TicTacToe())
  .action("MoveO", p -> game.moveTo(toGamePos(p.pick("coordinate", Position.class)), Player.O))
  .ignore("stuttered")
  .run();                                          // throws for unmatched actions
```

Step extraction (`StepExtractor`, port of `driver/step.rs`):

- default (`nondetPath` empty): remove `mbt::actionTaken` (string) and
  `mbt::nondetPicks` (record) from the state record; the remainder, navigated
  by `statePath`, is the spec state;
- otherwise: find the record at `nondetPath`, read `tag` as the action and
  `value` (`()` or record) as picks; strip the `mbt::*` vars if present;
- `NondetPicks` unwraps `Some/None` per entry (as `driver/nondet.rs`);
- all error messages are kept verbatim from the Rust version (they are part of
  the user experience and already well worded).

### 5.4 Trace generation (`trace` package)

```java
public sealed interface GenConfig permits RunConfig, TestConfig {
  String seed(); int nTraces(); List<String> toCommand(Path outDir);
}
public record RunConfig(Path spec, Optional<String> main, Optional<String> init, Optional<String> step,
                        OptionalInt maxSamples, OptionalInt maxSteps, String seed) implements GenConfig {}
public record TestConfig(Path spec, Optional<String> main, String test,
                         OptionalInt maxSamples, String seed) implements GenConfig {}
```

Command lines are identical to the Rust version (asserted by unit tests that
mirror `run.rs` / `test.rs` tests):

```
quint run  <spec> --seed <s> --max-samples <n> --n-traces <n> --out-itf <tmp>/run_{seq}.itf.json --mbt --verbosity 0 [--main …] [--init …] [--step …] [--max-steps …]
quint test <spec> --seed <s> --match ^<test>$ --max-samples <n> --out-itf <tmp>/test_{seq}.itf.json --verbosity 0 [--main …]
```

- `QuintCli` locates the executable: `QUINT_BIN` env/system property →
  `quint.cmd` on Windows → `quint` on PATH. A missing binary produces an
  actionable error ("Quint not found. Install with `npm i -g @informalsystems/quint`…").
- Traces are written to `Files.createTempDirectory("quint-connect-")`, read
  in file-name order (`{seq}` sorted numerically), and the directory is deleted
  when the returned `TraceSource` is closed (`try-with-resources`).
- Non-zero exit → `QuintException` carrying stderr, message "Quint returned
  non-zero code." — same as Rust.
- `TraceGenerator` is an interface (`Stream<ItfTrace> generate(GenConfig)`) so
  the Apalache backend (§9) and an "offline" backend that replays existing
  `*.itf.json` files can be plugged in.

Seed: `Seeds.resolve(Optional<String> explicit)` → explicit → `QUINT_SEED`
(system property, then env var) → random `0x%x` of a 32-bit value. Note the
Rust crate reads `QUINT_SEED` at **compile** time (`option_env!`); the Java
version reads it at run time, which is strictly more useful.

### 5.5 Runner and reporting (`runner`, `log` packages)

```java
public final class QuintConnect {
  public static <S> void run(String testName, Supplier<? extends Driver<S>> driver, GenConfig config);
  public static <S> Stream<TraceRun> traces(Supplier<? extends Driver<S>> driver, GenConfig config); // for JUnit
}
```

`run` mirrors `runner::run_test`:

1. `== Running model based tests for <name>` / `Generating N traces using
   <seed> as random seed …`
2. generate traces; fail if zero traces were produced (same message as Rust);
3. for each trace, for each state: extract `Step`, reject anonymous actions,
   `driver.step(step)`, then `checkState`;
4. `checkState`: `spec = ItfMapper.map(step.specState(), driver.stateType())`,
   `impl = driver.state()`; if `!Objects.equals(spec, impl)` render both with a
   pretty-printer (`ItfDisplay` for the spec side, `toString`/reflection for
   the impl side — both sides are converted back to a canonical `ItfValue`
   via Jackson so the diff compares like with like) and print a unified diff
   (`java-diff-utils`, headers `specification` / `implementation`);
5. on failure: `[FAIL] <name>` and `Reproduce this error with QUINT_SEED=<seed>`;
   the thrown `QuintConnectException` carries trace index, step index, action
   and the diff so JUnit shows them in the failure message.

Logging (`Logger`): `title/info/success/error/trace(level, …)` writing to
`System.err`, 3-space indentation of multi-line messages, ANSI colors
disabled when `NO_COLOR` is set or the console is not a TTY, verbosity from
`QUINT_VERBOSE` (system property or env; default 0).

## 6. Module `junit` — JUnit 5 integration

The proc macros become annotations on a test method that receives a
`TraceReplay` and hands it the driver to replay against:

```java
class TicTacToeMbt {
  @QuintRun(spec = "spec/tictactoe.qnt", maxSamples = 1)
  void simulation(TraceReplay traces) { traces.replay(TicTacToeDriver::new); }

  @QuintTest(spec = "spec/two_phase_commit.qnt", test = "commitTest")
  void commit(TraceReplay traces) { traces.replay(TwoPhaseCommitDriver::new); }
}
```

`TraceReplay.replay(Supplier)` creates the driver once per annotated method and
reuses it for every invocation (the Rust runner shares one driver across all
traces); `replay(Driver)` uses the given instance. A method that does not call
`replay` fails with a configuration error so a forgotten call cannot pass
silently. Tests of the extension itself swap the trace generator by registering
`QuintConnectExtension.runner(QuintConnect.using(FileTraceGenerator…))` with
`@RegisterExtension`.

- `@QuintRun(spec, main, init, step, maxSamples, maxSteps, seed)` and
  `@QuintTest(spec, main, test, maxSamples, seed)` are `@TestTemplate`
  meta-annotations with `@ExtendWith(QuintConnectExtension.class)`.
- `QuintConnectExtension implements TestTemplateInvocationContextProvider`
  generates traces **once** per annotated method and yields one invocation per
  trace (display name `[Trace 3/100] seed=0x…`). This gives per-trace
  pass/fail in IDEs and reports, and the seed appears in every display name.
  A `@QuintRun(singleInvocation = true)` switch restores the Rust behaviour of
  one test = all traces, for users with thousands of traces.
- `spec` is resolved relative to the project directory (Gradle's working dir is
  the module root, like `cargo test`); absolute paths and classpath resources
  (`classpath:spec/foo.qnt`, copied to a temp file) are also accepted.
- Configuration validation errors (`spec` empty, `test` empty on `@QuintTest`)
  fail at test discovery with the same messages the Rust macros emit at
  compile time (`Missing required attribute `spec``).
- A `@TestFactory` helper (`QuintDynamicTests.of(driverSupplier, config)`)
  covers users who want to build configs programmatically.

## 7. Examples (port of `connect/examples`)

- `tictactoe`: `TicTacToe` game class + `TicTacToeDriver` + `GameState`
  record; demonstrates sum types (`Square`, `Player`), `Map<Integer, Map<Integer, Square>>`
  with non-string keys, optional picks (`corner?`, `coordinate?`).
- `two_phase_commit`: port of `system.rs` (coordinator/participant state
  machines) and the Choreo-based spec; demonstrates `DriverConfig` with
  `statePath("two_phase_commit::choreo::s")` (the checked state is a record
  `{ system }`, since `Class<S>` cannot name a `Map<String, ProcState>`) and
  `nondetPath("two_phase_commit::choreo::s", "extensions", "actionTaken")`,
  and both `@QuintRun` and `@QuintTest`.

The `.qnt` files are copied verbatim from upstream (Apache-2.0).

## 8. Testing strategy for the library itself

- Unit tests port every `#[cfg(test)]` module from Rust: command-line
  construction, `extract_*` step functions, option unwrapping, indentation,
  display. ITF fixtures (small `*.itf.json` produced once with the real CLI)
  live under `src/test/resources`.
- `ItfMapper` tests: one test per row of the table in §5.2, including error
  messages for missing `tag`, wrong variant, overflow.
- Integration tests (tag `quint`, run in CI where the CLI is installed):
  the two examples pass; a deliberately broken driver fails with a diff and the
  seed hint; `QUINT_SEED` makes two runs produce identical traces.
- A "bad spec" test asserts that an anonymous action in `step` yields the
  documented error.

## 9. Extension point: Apalache / TLA+ traces

A follow-up question during the initial research was whether Apalache/TLA+
could feed the same pipeline. Apalache emits the **same ITF schema**
(`apalache-mc check --out-itf=…`), but without `mbt::actionTaken` /
`mbt::nondetPicks`; the model must record the action itself, e.g. an
`actionTaken` variable of the form `{ tag: "Deposit", value: { amount: 3 } }`.

The design above already supports this without API changes:

- `TraceGenerator` is pluggable → add `ApalacheTraceGenerator`
  (`ApalacheConfig(spec, inv, length, …)`).
- `DriverConfig.nondetPath("actionTaken")` is exactly the "sum type at a path"
  extraction mode that Choreo specs use today.
- Type hints in ITF `#meta.varTypes` can later feed `ItfMapper` diagnostics.

Implemented in PR10 (`ApalacheConfig`, `ApalacheCli`, `ApalacheTraceGenerator`,
`docs/apalache.md`); Apalache 0.62 writes the ITF traces into its `--run-dir`,
so no `--out-itf` flag is needed.

## 10. Open questions / decisions to confirm

1. Package / Maven coordinates: proposal `io.github.nsyee:quint-connect-core`,
   `io.github.nsyee:quint-connect-junit`, package `io.github.nsyee.quintconnect`.
2. Per-trace JUnit invocations (proposed default) vs. one invocation per
   annotated method (Rust behaviour).
3. Driver lifecycle: reuse one instance across traces (Rust) vs. a fresh
   instance per trace. Proposal: reuse by default, `freshDriverPerTrace` opt-in.
4. Minimum Java: 21 (proposed) vs. 17 (would lose record patterns / sealed
   `switch` exhaustiveness in user code but not in the library API).
