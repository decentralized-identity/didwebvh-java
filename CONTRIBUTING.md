# Contributing to didwebvh-java

Thank you for your interest in contributing! This guide will help you get started.

## Getting Started

### Prerequisites

- **Java 11 or later** — the library supports Java 11+, but contributors should use
  [Eclipse Temurin 21](https://adoptium.net/) for local verification.
- **Git**
- That's it. Maven is included in the project via the Maven Wrapper (`mvnw`).

### Setup

```bash
git clone https://github.com/decentralized-identity/didwebvh-java.git
cd didwebvh-java
./mvnw clean verify
```

This will compile all modules, run all tests, check code style, and generate coverage reports. If it passes, you're ready to contribute.

Use JDK 21 when running the full local build. The CI matrix runs Java 11, 17, 21, and 25, but SpotBugs is
skipped on JDK 22+ because the current SpotBugs toolchain does not support those class files. Running
`./mvnw clean verify` on JDK 25 is useful as a smoke test, but it does not match the SpotBugs checks that run
on the Java 11, 17, and 21 CI jobs.

On macOS, after installing JDK 21:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
./mvnw clean verify
```

### IDE Setup

The project works with any Java IDE. Recommended:

- **IntelliJ IDEA** — open the root `pom.xml` as a project
- **VS Code** — install the "Extension Pack for Java" extension
- **Eclipse** — import as existing Maven project

## How to Contribute

### Reporting Bugs

Open a [GitHub Issue](https://github.com/decentralized-identity/didwebvh-java/issues) with:
- What you expected to happen
- What actually happened
- Steps to reproduce
- Java version and OS

### Suggesting Features

Open a GitHub Issue with the `enhancement` label. Describe the use case and why it matters.

### Submitting Code

1. **Fork** the repository and create a branch from `main`:
   ```bash
   git checkout -b feat/your-feature
   ```

2. **Make your changes.** Follow the coding standards below.

3. **Write tests.** Every change should have corresponding tests.

4. **Run the full build:**
   ```bash
   ./mvnw clean verify
   ```
   This must pass on JDK 21 before opening a PR. It runs:
   - Compilation (Java 11 target)
   - All unit and integration tests
   - Checkstyle (Google Java Style, 120 char line length)
   - SpotBugs static analysis
   - JaCoCo coverage report

5. **Commit** with a clear message following [Conventional Commits](https://www.conventionalcommits.org/):
   ```
   feat: add witness threshold validation
   fix: correct SCID placeholder replacement in nested objects
   test: add test vectors for pre-rotation key hash
   docs: clarify Signer interface javadoc
   ```

6. **Push** and open a **Pull Request** against `main`.

### PR Review Process

- PRs require at least one review before merging
- CI must pass on all Java versions (11, 17, 21, 25)
- Local pre-PR verification should use JDK 21 so SpotBugs runs before CI
- Coverage should not decrease
- Keep PRs focused — one feature or fix per PR

## Coding Standards

- **Java 11** — no `var` in public API, no records, no sealed classes
- **Google Java Style** with 120 character line length (enforced by Checkstyle)
- **No wildcard imports**
- **Javadoc** on all public classes and methods
- **Package-private by default** — only expose what callers need
- **Immutable model classes** where possible
- **Unchecked exceptions** — wrap errors in `DidWebVhException` subclasses
- **No `@author` tags** — use `git blame`

## Testing

- **JUnit 5** for all tests
- **AssertJ** for assertions (`assertThat(...)` style)
- **Mockito** for mocking
- **MockWebServer** for HTTP tests
- Tests go in `src/test/java/` mirroring the main source package structure
- Test resources (JSON fixtures, test vectors) go in `src/test/resources/`

Run specific tests:
```bash
./mvnw test -pl didwebvh-core -Dtest=ScidGeneratorTest
```

Run only integration tests:
```bash
./mvnw verify -pl didwebvh-core -DskipUnitTests
```

## Project Structure

```
didwebvh-java/
  pom.xml                  # Parent POM
  didwebvh-core/           # Core library (the main module)
  didwebvh-signing-local/  # Local key file signer adapter
  didwebvh-wizard/         # Interactive CLI
```

For deeper technical context — package layout, design decisions, dependency rationale — see [AGENTS.md](docs/AGENTS.md).

For the full architecture and algorithms — see [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Implementation Roadmap

The project is built incrementally across 14 iterations described in [ITERATIONS.md](docs/dev/ITERATIONS.md). If you want to pick up work, check which iteration is in progress and look for open issues tagged with that iteration.

## Code of Conduct

Be respectful, constructive, and collaborative. We follow the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

## Questions?

Open a GitHub Discussion or Issue. We're happy to help you get started.
