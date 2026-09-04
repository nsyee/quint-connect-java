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

[Unreleased]: https://github.com/nsyee/quint-connect-java/compare/main...HEAD
