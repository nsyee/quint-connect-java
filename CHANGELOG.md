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

[Unreleased]: https://github.com/nsyee/quint-connect-java/compare/main...HEAD
