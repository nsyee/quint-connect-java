# Apalache / TLA+ traces

[Apalache](https://apalache-mc.org/) model-checks TLA+ specifications and can
write the traces it finds in the same ITF format that Quint produces. The
optional `ApalacheTraceGenerator` in `core` feeds those traces to the regular
`QuintConnect` runner, so a TLA+ specification can drive the same drivers,
state comparison and diff output as a Quint one.

## Requirements

- Apalache 0.62 or newer (tested with 0.62.2). Download a release from
  <https://github.com/apalache-mc/apalache/releases>, unpack it and put its
  `bin/` directory on `PATH`, or point `APALACHE_BIN` (environment variable or
  system property) at the `apalache-mc` script (`apalache-mc.bat` on Windows).
- A JDK for Apalache itself (it is a JVM application; see its
  [installation guide](https://apalache-mc.org/docs/apalache/installation/index.html)).

## Modelling convention: `mbt_action_taken`

Apalache has no `--mbt` mode; ITF traces carry only the state variables of the
specification. To tell the driver which action the specification took (and with
which parameters), the specification must record it in a state variable shaped
like a Quint sum type:

```tla
VARIABLES
    \* @type: Int;
    count,
    \* @type: { tag: Str, value: { amount: Int } };
    mbt_action_taken

Init ==
    /\ count = 0
    /\ mbt_action_taken = [tag |-> "Init", value |-> [amount |-> 0]]

Increment ==
    \E n \in 1..3:
        /\ count' = count + n
        /\ mbt_action_taken' = [tag |-> "Increment", value |-> [amount |-> n]]
```

- `tag` is the action name the driver `switch`es on.
- `value` is a record whose fields are the nondeterministic picks
  (`step.pick("amount", …)`). Every variant must have the same record shape
  because TLA+ variables are monomorphic; use dummy values (`amount |-> 0`) for
  actions without parameters, or model the payload as a
  [variant](https://apalache-mc.org/docs/lang/variants.html).
- The name is free; `mbt_action_taken` mirrors Quint's `mbt::actionTaken`.

### `\* @type` annotations

Apalache needs [type annotations](https://apalache-mc.org/docs/HOWTOs/howto-write-type-annotations.html)
on every variable and constant. Those are what make the ITF output typed
(`#meta.varTypes`) and what let the parser distinguish records from maps. The
ITF encoding is the same as Quint's: integers as `{"#bigint": "3"}`, records as
JSON objects, sets as `{"#set": […]}`, functions as `{"#map": […]}`, and so
on — see `docs/design.md`.

## Configuring the driver

Point the framework at the action variable with `DriverConfig.nondetPath`:

```java
@Override
public DriverConfig config() {
  return DriverConfig.DEFAULT.nondetPath("mbt_action_taken");
}
```

`mbt_action_taken` is a regular state variable, so it stays part of the state
that is compared against `state()`. Reproduce it in the Java state type as a
sealed interface — `@ItfVariant(record = true)` makes the one-component
variants round-trip as `[tag |-> …, value |-> [amount |-> n]]`:

```java
sealed interface Action permits Init, Increment, Decrement, Reset {}

@ItfVariant(record = true) record Init(long amount) implements Action {}
@ItfVariant(record = true) record Increment(long amount) implements Action {}
// …

record CounterState(long count, @JsonProperty("mbt_action_taken") Action actionTaken) {}
```

Alternatively nest the "real" state in one variable and select it with
`DriverConfig.statePath(...)`, exactly like Choreo specifications do.

## Generating traces

`ApalacheConfig` describes an `apalache-mc simulate` (random runs) or
`apalache-mc check` (counterexamples to an invariant) invocation:

```java
QuintConnect runner = QuintConnect.using(ApalacheTraceGenerator.create());

// 5 random runs of up to 8 steps, seeded (Apalache's smt.randomSeed)
runner.runTest(
    "Counter simulate",
    CounterDriver::new,
    ApalacheConfig.simulate(Path.of("spec/Counter.tla"), "0x2a").withMaxRuns(5).withLength(8));

// up to 3 distinct (by View) counterexamples to BelowThree, at most 4 steps long
runner.runTest(
    "Counter check",
    CounterDriver::new,
    ApalacheConfig.check(Path.of("spec/Counter.tla"), "1")
        .withInvariants("BelowThree")
        .withLength(4)
        .withMaxErrors(3, "View"));
```

Both forms accept `withInit`, `withNext`, `withCinit` (constant initialiser),
`withInvariants` and `withLength`. `simulate` writes `example<i>.itf.json`,
`check` writes `violation<i>.itf.json`; the generator returns only the numbered
files, from a temporary directory deleted when the `TraceSource` is closed.
Apalache's exit code 12 (invariant violated) is treated as success, everything
else non-zero raises `QuintException` with Apalache's output.

The seed is Apalache's SMT random seed; it makes runs *mostly* reproducible but,
unlike Quint, not byte-for-byte identical.

There is no JUnit meta-annotation for Apalache yet; use the runner directly
from a plain `@Test` as the
[`counter` example](../examples/src/test/java/io/github/nsyee/quintconnect/examples/counter)
does, or wrap it in `QuintDynamicTests.of(runner, driver, config)`.

## Running the tests

The tests that spawn `apalache-mc` are tagged `apalache` and skipped with
`./gradlew check -PskipApalache`.
