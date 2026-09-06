# quint-connect-java

[![CI](https://github.com/nsyee/quint-connect-java/actions/workflows/ci.yml/badge.svg)](https://github.com/nsyee/quint-connect-java/actions/workflows/ci.yml)

A Java port of [quint-connect](https://github.com/informalsystems/quint-connect):
model-based testing of Java code against [Quint](https://quint-lang.org/)
specifications.

quint-connect generates execution traces from a Quint specification and
replays them against your implementation through a small *driver*. After every
step it checks that the implementation state, abstracted to the specification's
state type, is equal to the state the specification is in — and prints a diff
when it is not.

- [Design](docs/design.md)
- [Work plan](docs/work-plan.md)
- [Apalache / TLA+ traces](docs/apalache.md)
- [Changelog](CHANGELOG.md)
- [Examples](examples/) — `tictactoe`, `two_phase_commit` and the TLA+ `counter`

## Requirements

- Java 21 or newer.
- The Quint CLI on `PATH`: `npm i -g @informalsystems/quint` (or point
  `QUINT_BIN` at it, see [Configuration](#configuration)).
- Optionally [Apalache](https://apalache-mc.org/) on `PATH` (or `APALACHE_BIN`)
  to generate traces from TLA+ specifications, see
  [docs/apalache.md](docs/apalache.md).
- JUnit 5 for the `quint-connect-junit` module. The `core` module has no test
  framework dependency and can be used from any runner or a plain `main`.

## Modules

| Module     | Coordinates                           | Purpose                                                  |
| ---------- | ------------------------------------- | -------------------------------------------------------- |
| `core`     | `io.github.nsyee:quint-connect-core`  | ITF parsing, trace generation, framework-agnostic runner |
| `junit`    | `io.github.nsyee:quint-connect-junit` | JUnit 5 extension (`@QuintRun`, `@QuintTest`)            |
| `examples` | not published                         | tictactoe, two-phase-commit ports and a TLA+ counter     |

The artifacts are not published yet (see PR9 in the [work plan](docs/work-plan.md));
until they are, consume the modules through a
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html).

## Quick Start

The complete version of this walkthrough is the
[`tictactoe` example](examples/src/test/java/io/github/nsyee/quintconnect/examples/tictactoe).

### 1. Define your specification

Write the specification in Quint, e.g. `spec/tictactoe.qnt`. Traces are
generated with `quint run --mbt`, which records the name of the action taken at
every step and the values picked by its `nondet` bindings:

```quint
type Player = X | O
type Square = Occupied(Player) | Empty

var board: int -> (int -> Square)
var nextTurn: Player

action MoveO = all {
  nextTurn == O,
  nondet coordinate = boardCoordinates.filter(isEmpty).oneOf()
  Move(O, coordinate),
}
```

### 2. Implement the driver

A `Driver<S>` connects the implementation to the specification. `S` is the
specification state as a Java type — usually a `record` whose components are
named like the Quint state variables — and `state()` is the *abstraction
function* that computes it from the implementation:

```java
import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.itf.mapper.ItfMapper;

final class TicTacToeDriver implements Driver<TicTacToeDriver.GameState> {

  sealed interface Square permits Occupied, Empty {}
  record Occupied(Player player) implements Square {}
  record Empty() implements Square {}

  record GameState(Map<Integer, Map<Integer, Square>> board, Player nextTurn) {}
  record Coordinate(int x, int y) {}          // Quint `(int, int)`

  private final ItfMapper mapper = ItfMapper.defaultMapper();
  private TicTacToe game = new TicTacToe();

  @Override
  public void step(Step step) {
    switch (step.action()) {
      case "init" -> game = new TicTacToe();
      case "MoveX" -> {
        Coordinate at =
            step.optionalPick("corner", mapper.to(Coordinate.class))
                .or(() -> step.optionalPick("coordinate", mapper.to(Coordinate.class)))
                .orElse(new Coordinate(2, 2));
        game.moveTo(toPosition(at), Player.X);
      }
      case "MoveO" ->
          game.moveTo(toPosition(step.pick("coordinate", mapper.to(Coordinate.class))), Player.O);
      default -> throw Step.unimplemented(step);
    }
  }

  @Override
  public GameState state() { /* build the board map from the game */ }

  @Override
  public Class<GameState> stateType() {
    return GameState.class;
  }
}
```

`step.action()` is the name of the Quint action; `step.pick(name, converter)`
returns a `nondet` value (failing if it is absent) and `optionalPick` returns
an `Optional` for picks that only some branches of the action make. Unknown
actions should fail with `Step.unimplemented(step)`, or use the `Actions`
dispatcher, which does that by default:

```java
Actions.on(step)
    .action("init", () -> game = new TicTacToe())
    .action("MoveO", s -> game.moveTo(position(s), Player.O))
    .ignore("stuttered")
    .run();
```

### 3. Create tests

With the `quint-connect-junit` module, annotate a test method that takes a
`TraceReplay` and hands it the driver:

```java
import io.github.nsyee.quintconnect.junit.QuintRun;
import io.github.nsyee.quintconnect.junit.QuintTest;
import io.github.nsyee.quintconnect.junit.TraceReplay;

class MbtTest {

  // Simulation: many random traces from `quint run --mbt`.
  @QuintRun(spec = "spec/tictactoe.qnt", maxSamples = 10)
  void simulation(TraceReplay traces) {
    traces.replay(TicTacToeDriver::new);
  }

  // One trace from a named `run` of the specification, via `quint test`.
  @QuintTest(spec = "spec/two_phase_commit.qnt", test = "commitTest")
  void commit(TraceReplay traces) {
    traces.replay(TwoPhaseCommitDriver::new);
  }
}
```

The method runs once per generated trace, with display names such as
`[Trace 3/10] seed=0x1f2e3d4c`, so IDEs and reports show per-trace results.
`singleInvocation = true` replays all traces in one invocation instead. `spec`
is resolved against the working directory (the module directory under Gradle
and Maven), as an absolute path, or as a `classpath:` resource.

`TraceReplay.replay(Supplier)` creates the driver once per test method and
reuses it for every trace, like the Rust crate; the `init` action is expected
to reset it. Pass an instance with `replay(Driver)` to control the lifecycle
yourself, or build tests programmatically with `QuintDynamicTests.of(...)` in a
`@TestFactory`.

Without JUnit, the same run is:

```java
QuintConnect.run(
    "tictactoe",
    TicTacToeDriver::new,
    RunConfig.of(Path.of("spec/tictactoe.qnt"), Seeds.resolve()).withMaxSamples(10));
```

### 4. Run the tests

```sh
./gradlew test
QUINT_VERBOSE=1 ./gradlew test    # print every trace and step (actions, picks)
```

A divergence is reported with the trace, step and action, followed by a unified
diff of the two states and the seed to reproduce it:

```text
State invariant failed (trace 1, step 3, action `MoveX`)
--- specification
+++ implementation
@@ -1,26 +1,26 @@
 {
   board: Map(
     1 -> Map(
-      1 -> Empty,
+      1 -> Occupied(
+        X,
+      ),
…
[FAIL] [Trace 1/10] seed=0xf351e567
Reproduce this error with `QUINT_SEED=0xf351e567`
```

## Tips and Tricks

### Mapping Quint values to Java

`ItfMapper` converts values from the trace into Java types. It is a
pre-configured Jackson `ObjectMapper`, so Jackson annotations such as
`@JsonProperty` work, and unknown fields are ignored (you only need to declare
the parts of the state you want to compare).

| Quint                      | Java                                                            |
| -------------------------- | --------------------------------------------------------------- |
| `int`                      | `int`, `long`, `BigInteger` (overflow is an error)              |
| `bool`, `str`              | `boolean`, `String`                                             |
| `{ a: 1, b: "x" }`         | `record` or POJO with matching component names                  |
| `(1, "x")` (tuple)         | `record` with positional components, or `List<Object>`         |
| `Set[T]`                   | `Set<T>`                                                        |
| `List[T]`                  | `List<T>`                                                       |
| `K -> V`                   | `Map<K, V>` — keys may be any type, not only strings            |
| `A \| B(int)` (sum type)   | `sealed interface` with `record` variants, or `enum` if tag-only |
| `Option[T]`                | `Optional<T>`                                                   |

### Sum types

Quint serialises sum types as `{ tag, value }`. Declare a sealed interface
whose permitted records are named after the variants; the payload goes into
the record's components (one component takes `value` as is, several take a
tuple positionally, none ignores it):

```java
sealed interface Message permits CoordinatorCommit, ParticipantPrepared {}
record CoordinatorCommit() implements Message {}
record ParticipantPrepared(String node) implements Message {}
```

Sum types without payloads can simply be an `enum`:

```java
enum Stage { Working, Prepared, Committed, Aborted }
```

Use `@ItfVariant("Name")` when the Java name must differ from the Quint tag,
and `@ItfVariant(record = true)` when a one-component variant carries a
one-field record (`Prepared({ node: "p1" })`) rather than a bare value
(`Prepared("p1")`), so that it is rendered back correctly in diffs.

### Optional fields

`Option[a] = Some(a) | None` maps to `Optional<T>` without further ado:

```java
record MyState(Optional<String> leader) {}
```

Nondet picks are unwrapped the same way: `step.pick("x", …)` on a `Some(v)`
yields `v`, and `step.optionalPick("x", …)` is empty for `None` or a missing
pick.

### Non-string map keys

Quint maps are serialised as pairs, so `Map<Integer, …>`, `Map<Coordinate, …>`
or any other key type work directly — see `GameState.board` above, which is
`int -> (int -> Square)`.

### Nondeterminism and anonymous actions

Quint records action names and `nondet` values while simulating, but only for
*named* actions. If `step` contains an anonymous action the run fails with
`An anonymous action was found!`:

```quint
action step = any {
  action1,
  all {  // <- anonymous action!
    action2,
    action3
  }
}
```

Name every alternative of `step` instead:

```quint
action step = any {
  action1,
  action2_and_3
}

action action2_and_3 = all {
  action2,
  action3
}
```

### Keeping the driver small

The specification state type only has to cover what you want to compare: a
`record` that declares two of five Quint variables checks those two. Return
`Void` from `stateType()` (`Driver<Void>`) to skip state checking altogether
and only exercise the implementation with the generated action sequences.

## Configuration

### Driver configuration

If the state to check is nested inside a global variable, or if the
specification tracks the action taken itself instead of relying on Quint's
`mbt::*` variables, override `Driver.config()`:

```java
@Override
public DriverConfig config() {
  return DriverConfig.statePath("two_phase_commit::choreo::s")
      .nondetPath("two_phase_commit::choreo::s", "extensions", "actionTaken");
}
```

- `statePath` selects the sub-record of the ITF state that is compared with
  `state()`.
- `nondetPath` points at a sum type whose tag is the action name and whose
  payload (a record) holds the picks; it replaces `mbt::actionTaken` /
  `mbt::nondetPicks`. This is how specifications written with
  [Choreo](https://github.com/informalsystems/choreo/) are checked, see the
  [`two_phase_commit` example](examples/src/test/java/io/github/nsyee/quintconnect/examples/twophasecommit).

### Verbosity

`QUINT_VERBOSE` (environment variable or system property) controls the output
written to `System.err`:

- `0` (default): titles, `[OK]`/`[FAIL]` and failure diffs only
- `1`: plus every trace and step (action, picks, next state) and the diff
- `2`: plus the raw ITF state each step and state were derived from

Colors follow `NO_COLOR` / `FORCE_COLOR` and are off when not writing to a
terminal.

### Reproducible tests

Every failure prints the seed the traces were generated with:

```text
Reproduce this error with `QUINT_SEED=0xf351e567`
```

Set it to generate the same traces again:

```sh
QUINT_SEED=0xf351e567 ./gradlew test --tests '*TicTacToeMbtTest*'
```

The seed can also be fixed per test with `@QuintRun(seed = "0x…")`, or with
`-DQUINT_SEED=…` as a system property.

### Quint executable

The Quint CLI is looked up as `quint` on `PATH` (`quint.cmd` on Windows).
`QUINT_BIN` (environment variable or system property) overrides the executable,
for instance to use a project-local install or a wrapper script.

### Apalache / TLA+ specifications

`ApalacheTraceGenerator` runs `apalache-mc simulate` or `apalache-mc check`
(`ApalacheConfig.simulate(spec, seed)` / `ApalacheConfig.check(spec, seed)`)
and replays the ITF traces Apalache writes. The specification must record the
action it took in a sum-type-shaped variable such as `mbt_action_taken`, which
the driver selects with `DriverConfig.nondetPath("mbt_action_taken")`. The
executable is `apalache-mc` on `PATH` (`apalache-mc.bat` on Windows) or
`APALACHE_BIN`. See [docs/apalache.md](docs/apalache.md) and the
[`counter` example](examples/src/test/java/io/github/nsyee/quintconnect/examples/counter).

## Building this repository

Requirements: JDK 21 or newer to run Gradle (the Java 21 compile/test
toolchain itself is provisioned automatically via foojay) and, for the
integration tests, the Quint CLI and Apalache on `PATH`.

```sh
./gradlew spotlessCheck check   # format check, Error Prone, tests
./gradlew spotlessApply         # fix formatting
./gradlew check -PskipQuint     # skip tests that need the Quint CLI
./gradlew check -PskipApalache  # skip tests that need Apalache
```

The tests tagged `quint` / `apalache` (the examples and the CLI integration
tests) spawn the real CLI and are excluded by `-PskipQuint` / `-PskipApalache`.

## License

Apache-2.0, see [LICENSE](LICENSE). This is a derivative work of
Informal Systems' quint-connect, see [NOTICE](NOTICE). The example
specifications under `examples/spec` are copied from the upstream repository.
