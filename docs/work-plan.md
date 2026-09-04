# quint-connect-java — Work Plan

Delivery plan for the design in [design.md](design.md). Work is split into PRs
that each leave `main` green (`./gradlew check`) and independently reviewable.
Sizes are rough: S ≈ a few hundred lines, M ≈ up to ~1k lines including tests,
L ≈ more.

```
PR1 skeleton ─► PR2 itf ─► PR3 trace gen ─┐
                    │                      ├─► PR6 runner ─► PR7 junit ─► PR8 examples ─► PR9 release
                    ├─► PR4 driver/step ───┤
                    └─► PR5 itf mapper ────┘                                            └─► PR10 apalache (optional)
```

PR3, PR4 and PR5 only depend on PR2 and can be developed in parallel.

## PR1 — Project skeleton and CI (S)

- Gradle (Kotlin DSL) multi-project: `core`, `junit`, `examples`; wrapper;
  Java 21 toolchain with foojay resolver; version catalog (`libs.versions.toml`).
- Dependencies pinned: Jackson, java-diff-utils, JUnit 5, Spotless
  (google-java-format), Error Prone.
- `LICENSE` (Apache-2.0), `NOTICE` crediting Informal Systems' quint-connect,
  `README.md` (short, links to docs), `CHANGELOG.md` (Keep a Changelog),
  `.gitignore`, `.editorconfig`.
- GitHub Actions `ci.yml`: matrix ubuntu/windows, setup-java 21, setup-node +
  `npm i -g @informalsystems/quint`, `./gradlew check`.
- Devin environment blueprint for this repo (JDK 21, Node + quint on PATH).

Acceptance: `./gradlew check` passes with an empty `core` module and one
placeholder test; CI green on both OSes.

## PR2 — ITF model, parser and display (M)

`core/…/itf`

- `ItfValue` sealed hierarchy, `ItfTrace`, `ItfState`, `ItfMeta`.
- `ItfParser` (Jackson `JsonNode` → `ItfValue`): `#bigint`, `#tup`, `#set`,
  `#map`, `#unserializable`, plain records, `#meta`, `loop`.
- Order-insensitive `equals`/`hashCode` for `Set` and `Map`.
- `ItfValues.asOption` (port of `value/option.rs`) and `ItfDisplay` (port of
  `value/display.rs`).
- Fixtures: `*.itf.json` for tictactoe and two-phase-commit (generated once
  with the CLI, committed under `src/test/resources`).

Tests: parser round-trips on fixtures; display output equals the Rust
rendering for a curated list of values; option unwrapping tests ported 1:1.

## PR3 — Trace generation via the Quint CLI (M)

`core/…/trace`

- `GenConfig`, `RunConfig`, `TestConfig` with `toCommand(Path)`.
- `QuintCli` (executable lookup incl. Windows `quint.cmd`, `QUINT_BIN`),
  `QuintTraceGenerator` (ProcessBuilder, temp dir, stderr capture,
  `QuintException`), `TraceSource` (`Closeable`, deletes the temp dir).
- `TraceGenerator` interface + `FileTraceGenerator` (replays existing files;
  used by tests and useful for users with pre-generated traces).
- `Seeds.resolve` (`QUINT_SEED` property/env → random `0x…`).

Tests: command-line strings identical to the Rust unit tests (`run.rs`,
`test.rs`); `FileTraceGenerator` over fixtures; integration test tagged
`quint` that runs the real CLI on `tictactoe.qnt` and parses the output.

## PR4 — Step extraction and driver API (M)

`core/…/driver`

- `Driver<S>`, `DriverConfig`, `Step`, `NondetPicks`, `StepExtractor`
  (mbt-vars mode and sum-type-path mode), `Actions` fluent dispatcher,
  `Step.unimplemented(step)`.
- `Step.toString()` reproducing the Rust `Display` (`Action taken:`,
  `Nondet picks:`, `Next state:` sections).

Tests: port of all `step.rs` and `nondet.rs` unit tests (same messages);
`Actions` tests: matched, ignored, unmatched → exception.

## PR5 — ITF → Java type mapping (L)

`core/…/itf/mapper`

- `ItfMapper` built on a Jackson `ObjectMapper` + `ItfModule`:
  `ItfValue` → `JsonNode` conversion, `#map` with non-string keys →
  `Map<K,V>`, `#set` → `Set<T>`, `#tup` → record/`List`, `#bigint` →
  `int/long/BigInteger` with overflow checks.
- Sum types: generic deserializer for sealed interfaces (variant by simple
  name or `@ItfVariant`), 0/1/N-component variant records; tag-only sum types
  → Java `enum`.
- `Optional<T>` from `Some/None`.
- Reverse direction (`Object → ItfValue`) for canonical diff rendering of the
  implementation state.
- Error messages wrapped with the Rust hint ("Failed to deserialize
  specification's state. Please check the docs for tips and tricks…").

Tests: one test per mapping rule, negative tests for wrong tag / missing
field / overflow, `GameState` and `SpecState` from the examples mapped from
fixtures.

## PR6 — Runner, state check, diff and logging (M)

`core/…/runner`, `core/…/log`

- `Logger` (`title/info/success/error/trace`, indentation, colors,
  `QUINT_VERBOSE`).
- `QuintConnect.run(...)`: orchestration exactly as `runner/mod.rs`
  (zero-traces check, anonymous action check, per-step replay, `checkState`
  with unified diff, `[OK]/[FAIL]`, `Reproduce this error with QUINT_SEED=…`).
- `QuintConnectException` with trace/step/action context.
- `QuintConnect.traces(...)` streaming API used by the JUnit module.

Tests: end-to-end over `FileTraceGenerator` fixtures with a correct driver
(passes), a diverging driver (fails with expected diff text), and a driver
that throws in `step` (error propagates with trace/step index).

## PR7 — JUnit 5 extension (M)

`junit/…`

- `@QuintRun`, `@QuintTest` (`@TestTemplate` meta-annotations),
  `QuintConnectExtension` (`TestTemplateInvocationContextProvider`): one
  invocation per trace with display name `[Trace i/n] seed=…`;
  `singleInvocation` option; `spec` path resolution (project-relative,
  absolute, `classpath:`); annotation validation with the Rust error messages.
- `QuintDynamicTests` `@TestFactory` helper.

Tests: JUnit Platform Test Kit (`EngineTestKit`) verifying discovery,
display names, per-trace failures and validation errors; runs on fixtures via
a `FileTraceGenerator` injected through an extension parameter for tests.

## PR8 — Examples and user documentation (M)

`examples/`

- `tictactoe` and `two_phase_commit` ports (implementation + driver + spec
  files), run as part of `./gradlew check` under the `quint` tag.
- README: Quick Start (mirrors upstream README: spec → driver → annotations →
  run), Tips and Tricks (sum types, `Optional`, non-string map keys,
  nondeterminism / anonymous actions), Configuration (`DriverConfig`,
  `QUINT_VERBOSE`, `QUINT_SEED`, `QUINT_BIN`).
- Javadoc on all public API.

## PR9 — Release engineering (S)

- Maven publishing (`maven-publish` + signing) to Maven Central via Sonatype
  or to GitHub Packages (to decide); `CHANGELOG` 0.1.0; release workflow on
  tag; Dependabot for Gradle and Actions.

## PR10 — Optional: Apalache / TLA+ trace generator (M)

- `ApalacheTraceGenerator` + `ApalacheConfig` (`apalache-mc check --out-itf`),
  executable lookup, docs on the `actionTaken` modelling convention and
  `\* @type` annotations.
- Example: a small TLA+ counter spec with `mbt_action_taken`, driven through
  `DriverConfig.nondetPath("mbt_action_taken")`.

## Cross-cutting checklist (every PR)

- `./gradlew spotlessCheck check` green locally and in CI.
- Public API has Javadoc; error messages match upstream where the concept is
  the same.
- CHANGELOG entry under `Unreleased`.
- No behaviour hidden behind reflection hacks (`ItfMapper` is the only
  reflective component and it is confined to Jackson).

## Suggested order and effort

| Order | PR                    | Size | Depends on |
| ----- | --------------------- | ---- | ---------- |
| 1     | PR1 skeleton          | S    | —          |
| 2     | PR2 itf               | M    | PR1        |
| 3     | PR3 trace gen         | M    | PR2        |
| 3     | PR4 driver/step       | M    | PR2        |
| 3     | PR5 itf mapper        | L    | PR2        |
| 4     | PR6 runner            | M    | PR3–PR5    |
| 5     | PR7 junit             | M    | PR6        |
| 6     | PR8 examples/docs     | M    | PR7        |
| 7     | PR9 release           | S    | PR8        |
| 8     | PR10 apalache (opt.)  | M    | PR6        |

PR1–PR6 form a usable framework-agnostic core; PR7–PR8 make it a drop-in
replacement for the Rust user experience. Everything up to PR8 is roughly
three to four Devin sessions of work if PR3/4/5 are done in parallel.
