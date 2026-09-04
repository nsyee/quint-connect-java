# quint-connect-java

[![CI](https://github.com/nsyee/quint-connect-java/actions/workflows/ci.yml/badge.svg)](https://github.com/nsyee/quint-connect-java/actions/workflows/ci.yml)

A Java port of [quint-connect](https://github.com/informalsystems/quint-connect):
model-based testing of Java code against [Quint](https://quint-lang.org/)
specifications.

Status: skeleton — the build and CI are in place, the framework itself is
being ported module by module (see the work plan).

- [Design](docs/design.md)
- [Work plan](docs/work-plan.md)
- [Changelog](CHANGELOG.md)

## Modules

| Module                  | Coordinates                          | Purpose                                              |
| ----------------------- | ------------------------------------ | ---------------------------------------------------- |
| `core`                  | `io.github.nsyee:quint-connect-core` | ITF parsing, trace generation, framework-agnostic runner |
| `junit`                 | `io.github.nsyee:quint-connect-junit`| JUnit 5 extension (`@QuintRun`, `@QuintTest`)        |
| `examples`              | not published                        | tictactoe and two-phase-commit ports                 |

## Building

Requirements: JDK 21 or newer to run Gradle (the Java 21 compile/test
toolchain itself is provisioned automatically via foojay) and, for the
integration tests, the Quint CLI on `PATH` (`npm i -g @informalsystems/quint`).

```sh
./gradlew spotlessCheck check   # format check, Error Prone, tests
./gradlew spotlessApply         # fix formatting
./gradlew check -PskipQuint     # skip tests that need the Quint CLI
```

## License

Apache-2.0, see [LICENSE](LICENSE). This is a derivative work of
Informal Systems' quint-connect, see [NOTICE](NOTICE).
