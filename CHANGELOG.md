# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Gradle multi-project skeleton (`core`, `junit`, `examples`), Java 21
  toolchain, Spotless (google-java-format) and Error Prone.
- GitHub Actions CI on Ubuntu and Windows with the Quint CLI installed.
- `itf` package: `ItfValue` sealed hierarchy with order-insensitive `Set`/`Map`
  equality, `ItfTrace`/`ItfState`/`ItfMeta`, `ItfParser` for ITF JSON
  (`#bigint`, `#tup`, `#set`, `#map`, `#unserializable`, `#meta`, `loop`),
  `ItfValues.asOption` and the Quint-like pretty-printer `ItfDisplay`.
- `trace` package: `RunConfig`/`TestConfig` (`GenConfig`) producing the same
  `quint run`/`quint test` command lines as the Rust crate, `QuintCli`
  executable lookup (`QUINT_BIN`, `quint.cmd` on Windows),
  `QuintTraceGenerator` (spawns the CLI, temp dir cleaned up by
  `TraceSource.close()`, stderr in `QuintException`), `FileTraceGenerator`
  replaying pre-generated `*.itf.json` files and `Seeds.resolve`
  (`QUINT_SEED` property/env → random `0x…`).
- `driver` package: `Driver<S>` interface and `DriverConfig` (`statePath` /
  `nondetPath`), `Step` (action, `NondetPicks` with `Some`/`None` unwrapping,
  spec state, Rust-compatible `toString`), `StepExtractor` supporting both the
  `mbt::actionTaken`/`mbt::nondetPicks` mode and the sum-type-at-a-path mode
  with the upstream error messages, `Step.unimplemented(step)` and the
  `Actions` fluent dispatcher (unmatched actions fail by default).
- `itf.mapper` package: `ItfMapper` (Jackson `ObjectMapper` + `ItfModule`)
  mapping `ItfValue` to user types — `#bigint` → `int`/`long`/`BigInteger`
  with overflow and coercion errors, `#set` → `Set`, `#map` with non-string
  keys → `Map<K,V>`, `#tup` → record (positional) or `List`, records with
  required non-`Optional` components, `Optional<T>` from `Some`/`None`,
  sealed interfaces from `{ tag, value }` (variant by simple name or
  `@ItfVariant`, 0/1/N components, nested sealed roots) and tag-only sum types
  → `enum`. `stateFromItf` wraps failures with the upstream "Failed to
  deserialize specification's state" hint; `to(type)` yields a `Step.pick`
  converter; `toItf(Object)` maps Java values back to canonical (sorted)
  `ItfValue`s for diff rendering.
- `log` package: `Logger` with `title`/`info`/`success`/`error`/`trace`,
  three-space indentation of multi-line messages, ANSI colors (`NO_COLOR`,
  `FORCE_COLOR`, `TERM`) and verbosity from `QUINT_VERBOSE` (property/env).
- `runner` package: `QuintConnect.run(name, driverSupplier, config)`
  orchestrating trace generation and replay like the Rust `runner` module
  (zero-trace and anonymous-action checks, per-step `driver.step`, spec vs.
  implementation state comparison with a unified diff, `[OK]`/`[FAIL]` and
  the `QUINT_SEED` reproduction hint), `QuintConnect.traces(...)` streaming
  one `TraceRun` per generated trace, `StateDiff` and `QuintConnectException`
  carrying trace/step/action context.
- `junit` module: `@QuintRun` / `@QuintTest` `@TestTemplate` meta-annotations
  backed by `QuintConnectExtension` (one invocation per generated trace named
  `[Trace i/n] seed=…`, `singleInvocation` for one test = all traces, `spec`
  resolved as project-relative / absolute / `classpath:` resource, Rust
  validation messages such as ``Missing required attribute `spec` ``), the
  injected `TraceReplay` parameter (`replay(driverSupplier)` shares one driver
  across traces, `replay(driver)`), `QuintConnectExtension.runner(...)` to
  swap the trace generator in tests, and the `QuintDynamicTests.of(...)`
  `@TestFactory` helper.
- `examples` module: ports of the upstream `tictactoe` (sum types, non-string
  map keys, optional picks) and `two_phase_commit` (Choreo spec,
  `DriverConfig.statePath`/`nondetPath`, `@QuintRun` and `@QuintTest`)
  examples with their `.qnt` specifications, run under the `quint` tag.
- README with Quick Start, Tips and Tricks and Configuration sections.

[Unreleased]: https://github.com/nsyee/quint-connect-java/compare/main...HEAD
