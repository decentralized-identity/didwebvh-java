# Agent and Contributor Guide

This document provides instructions for AI agents and human contributors working on the `didwebvh-java` project.

## Project Overview

This is a Java 11+ library implementing the [did:webvh v1.0 specification](https://identity.foundation/didwebvh/v1.0/). It enables creating, resolving, updating, and deactivating `did:webvh` DIDs with pluggable key management.

## Guiding Principles

1. **Simplicity over abstraction.** A developer should be able to read through the codebase and understand it without tracing through many layers. Use design patterns only when they earn their keep (e.g., the `Signer` adapter pattern, builders for log entry composition).
2. **SOLID, but not academic.** Single Responsibility and Dependency Inversion are key (the `Signer` interface is a good example). Don't create interfaces for things that will only ever have one implementation.
3. **Java 11 baseline.** No `var` in public API. No records. No sealed classes. Use standard Java 11 features. Dependencies must also support Java 11.
   Newer JDKs may run the library, but code must remain Java 11-compatible.
4. **Spec fidelity.** The spec TXT (`docs/spec/Webvh v1.0.txt`) is the source of truth. When in doubt, follow the spec literally. Reference spec section numbers in code comments for non-obvious logic.
5. **Test-driven.** Every public method must have tests. Spec-related logic (SCID generation, entry hash verification, log chain validation) must have tests against known test vectors.

## Repository Structure

```
didwebvh-java/
  pom.xml                          # Parent POM (multi-module Maven)
  didwebvh-core/                   # Core library
    pom.xml
    src/main/java/io/github/decentralized-identity/didwebvh/
      core/                        # Top-level API (DidWebVh facade)
      model/                       # Data model (LogEntry, Parameters, DidDocument, etc.)
      crypto/                      # Hash, SCID, entry hash, JCS canonicalization
      signing/                     # Signer interface and proof generation
      create/                      # DID creation logic
      resolve/                     # DID resolution and log processing
      update/                      # DID update, migration, deactivation
      validate/                    # Log chain validation, witness verification
      url/                         # DID URL parsing and DID-to-HTTPS transformation
      witness/                     # Witness data model and proof handling
      didweb/                      # Parallel did:web document generation
    src/test/java/...
    src/test/resources/            # Test vectors (JSONL files, JSON files)
  didwebvh-signing-local/          # Local JSON key file signer
    pom.xml
    src/main/java/io/github/decentralized-identity/didwebvh/signing/local/
      LocalKeySigner.java
  didwebvh-wizard/                 # Interactive CLI
    pom.xml
    src/main/java/io/github/decentralized-identity/didwebvh/wizard/
      WizardMain.java
      CreateWizard.java
      UpdateWizard.java
      ResolveWizard.java
```

## Key Technical Decisions

### Build System
- **Maven** with multi-module layout. Parent POM manages dependency versions and plugin configuration.
- Maven Wrapper (`mvnw`) is included so no local Maven install is required.

### Dependencies (Core module - keep minimal)
- `com.google.code.gson:gson` - JSON parsing/serialization (widely used, Java 11 compatible)
- `io.github.erdtman:java-json-canonicalization` - JSON Canonicalization Scheme (JCS, RFC 8785)
- `com.github.multiformats:java-multihash` - Multihash encoding
- `io.github.novacrypto:Base58` - Base58btc encoding
- `org.bouncycastle:bcprov-jdk15on` + `bcpkix-jdk15on` - Ed25519, cryptographic operations, Data Integrity proof signing/verification
- `com.squareup.okhttp3:okhttp` - HTTP client for DID resolution (optional, only in core for network resolution)

### Dependencies (Wizard module)
- `info.picocli:picocli` - CLI framework
- `org.jline:jline` - Interactive terminal input

### Dependencies (Test)
- `org.junit.jupiter:junit-jupiter` - JUnit 5
- `org.assertj:assertj-core` - Fluent assertions
- `org.mockito:mockito-core` - Mocking
- `com.squareup.okhttp3:mockwebserver` - HTTP mocking for resolver tests

### Cryptography
- **Ed25519** via BouncyCastle for signing and verification (required by spec: `eddsa-jcs-2022` cryptosuite)
- **SHA-256** for hashing (the only hash algorithm permitted by did:webvh:1.0)
- **JCS** (JSON Canonicalization Scheme, RFC 8785) for deterministic JSON serialization before hashing/signing
- **Multihash** for self-describing hash format
- **Base58btc** for encoding hashes and SCIDs
- **Multikey** format for public key representation in `updateKeys`

### The `Signer` Interface
```java
public interface Signer {
    /** Returns the key type string (e.g., "Ed25519"). */
    String keyType();

    /** Returns the verification method URI (e.g., "did:key:z6Mk...#z6Mk..."). */
    String verificationMethod();

    /** Signs the given data and returns the signature bytes. */
    byte[] sign(byte[] data) throws SigningException;
}
```

This is the primary extension point. Implementors can plug in:
- Local Ed25519 keys (provided by `didwebvh-signing-local`)
- AWS KMS (user implements `Signer` calling AWS SDK)
- External API (user implements `Signer` calling their signing service)
- HSM devices

### Data Integrity Proofs
The spec requires `eddsa-jcs-2022` cryptosuite with `proofPurpose: assertionMethod`. The proof generation flow:
1. JCS-canonicalize the log entry (without proof field)
2. Sign the canonicalized bytes using the `Signer`
3. Construct the `DataIntegrityProof` object with `type`, `cryptosuite`, `verificationMethod`, `proofPurpose`, `created`, `proofValue`
4. Attach the proof to the log entry

### Builders for Composition
Use builder pattern for `CreateDidConfig` and `UpdateDidConfig` so callers can compose their request step by step, then finalize with proof generation:

```java
CreateDidResult result = DidWebVh.create("example.com")
    .withSigner(signer)
    .withPortable(true)
    .withService("linked-domain", "https://example.com")
    .execute();
```

## Coding Standards

- **No wildcard imports.**
- **No `@author` tags.** Use git blame.
- **Javadoc on all public classes and methods.** One-liner is fine for getters.
- **Package-private by default.** Only expose what callers need.
- **Immutable model classes** where possible. Use builder or factory methods for construction.
- **No checked exceptions** in the public API. Wrap in `DidWebVhException` (unchecked).
- **Null safety.** Use `@Nullable` annotation where nulls are expected. Prefer `Optional` for return types that may be absent. Never accept null in public API methods without documenting it.

## Testing Standards

- **JUnit 5** for all tests.
- **Test vectors** from the spec (JSONL files) must be included in `src/test/resources/test-vectors/`.
- **Unit tests** for each public class.
- **Integration tests** for end-to-end flows (create -> update -> resolve -> validate).
- **Property-based tests** are welcome but not required.
- Every PR must pass: `./mvnw clean verify`
- Use JDK 21 for local full verification. SpotBugs is skipped on JDK 22+ in this repository, so a local
  JDK 25 run can pass while the Java 11, 17, or 21 CI jobs fail on SpotBugs findings.

## CI/CD

GitHub Actions workflows:
- **ci.yml**: Runs on every push to `main` and all PRs
  - Matrix build: Java 11, 17, 21, 25 on ubuntu-latest
  - `./mvnw clean verify` (compiles, tests, checks)
  - Code coverage via JaCoCo, uploaded to Codecov
  - SonarCloud quality gate
  - Checkstyle and SpotBugs static analysis. SpotBugs runs on Java 11, 17, and 21; it is skipped on Java 25
    by the Maven profile for JDK 22+.
- **release.yml**: Triggered on version tag push, publishes to Maven Central via Sonatype

## Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):
- `feat: add DID creation with SCID generation`
- `fix: correct entry hash calculation for subsequent entries`
- `test: add test vectors for log chain validation`
- `docs: update README with resolve example`
- `chore: configure JaCoCo for coverage reporting`

## How to Work on This Project

1. Read `docs/ARCHITECTURE.md` for the full technical design.
2. Read `docs/dev/ITERATIONS.md` for the ordered list of implementation tasks with detailed prompts.
3. Follow the iterations in order. Each iteration has clear acceptance criteria.
4. Run `./mvnw clean verify` with JDK 21 after every change to ensure tests, Checkstyle, SpotBugs, and
   coverage run locally.
5. Keep dependencies minimal. Don't add a library for something that can be done in 20 lines.
