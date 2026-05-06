# didwebvh-java

[![Java CI](https://github.com/decentralized-identity/didwebvh-java/actions/workflows/ci.yml/badge.svg)](https://github.com/decentralized-identity/didwebvh-java/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/decentralized-identity/didwebvh-java/branch/main/graph/badge.svg)](https://codecov.io/gh/decentralized-identity/didwebvh-java)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.decentralized-identity/didwebvh-java.svg)](https://central.sonatype.com/artifact/io.github.decentralized-identity/didwebvh-java)
[![Java Version](https://img.shields.io/badge/Java-11%2B-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=decentralized-identity_didwebvh-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=decentralized-identity_didwebvh-java)

A Java 11+ library for the [did:webvh](https://didwebvh.info/) (DID Web + Verifiable History) DID Method, v1.0.

**Create**, **resolve**, **update**, **migrate**, **deactivate**, and **publish a parallel `did:web`** document for `did:webvh` DIDs with pluggable key management (local keys, AWS KMS, external APIs, and more).

## Features

- Full did:webvh v1.0 specification support
- **Create** DIDs with SCID generation, authorization keys, and Data Integrity proofs
- **Resolve** DIDs from HTTPS URLs or local files with full log chain verification
- **Update** DID documents, rotate keys, change parameters, migrate to new domains, and deactivate
- **Witness** support with threshold-based approval (`did-witness.json`)
- **Pre-rotation** key commitment (`nextKeyHashes`) for forward security
- **DID portability** across domains while preserving verifiable history
- **Parallel did:web** document publishing for backward compatibility
- **Pluggable signing** via `Signer` interface (local Ed25519, AWS KMS, HSM, external API)
- **Interactive wizard** CLI for guided DID management
- **Java 11+** compatible, tested on Java 11, 17, 21, and 25

## Quick Start

### Maven

```xml
<dependency>
    <groupId>io.github.decentralizedidentity</groupId>
    <artifactId>didwebvh-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

This aggregate artifact depends on `didwebvh-core` and `didwebvh-signing-local`. Depend on
`didwebvh-core` directly if you plan to supply your own `Signer` implementation and do not
need the local-key adapter.

### Gradle

```groovy
implementation 'io.github.decentralized-identity:didwebvh-java:0.2.0'
```

## Library Usage

All DID operations are exposed as static entry points on `io.github.decentralizedidentity.didwebvh.core.DidWebVh`.
The examples below use `LocalKeySigner` from `didwebvh-signing-local`; see
[Pluggable Key Management](#pluggable-key-management) for custom `Signer` implementations.

### Create a DID

```java
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;

LocalKeySigner signer = LocalKeySigner.generate();

CreateDidResult result = DidWebVh.create("example.com", signer)
        .path("dids:alice")                        // optional URL path segment
        .portable(true)                            // allow future domain migration
        .ttl(3600)
        .alsoKnownAs(List.of("did:key:z6Mk..."))   // optional
        .execute();

String did = result.getDid();        // e.g. did:webvh:QmSCID:example.com:dids:alice
String logLine = result.getLogLine();// write this as the first line of did.jsonl
```

Save the signer material safely — you need it to sign every subsequent update:

```java
Files.writeString(Paths.get("did-secrets.json"), signer.toJson());
```

### Resolve a DID

```java
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.resolve.DidResolver;
import io.github.decentralizedidentity.didwebvh.core.resolve.ResolveOptions;

// Remote resolution over HTTPS:
ResolveResult remote = DidWebVh.resolve("did:webvh:QmSCID:example.com");

// Offline resolution from an already-downloaded did.jsonl:
String jsonl = Files.readString(Paths.get("did.jsonl"));
ResolveResult offline = new DidResolver().resolveFromLog(jsonl, did);

// Time-travel / version filtering:
ResolveOptions opts = ResolveOptions.builder().versionNumber(3).build();
ResolveResult older = new DidResolver().resolveFromLog(jsonl, did, opts);

System.out.println(offline.getDidDocument().asJsonObject());
```

### Update, Migrate, or Deactivate

`DidWebVhState` holds the validated log state and is the input to every update:

```java
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.update.UpdateDidResult;

DidWebVhState state = DidWebVhState.fromDidLog(did, jsonl);

// Replace the DID Document (same SCID, same domain):
UpdateDidResult rotated = DidWebVh.update(state, signer)
        .newDocument(updatedDocJsonObject)
        .execute();

// Or change only parameters (e.g. TTL and watchers):
Parameters delta = new Parameters();
delta.setTtl(120);
delta.setWatchers(List.of("https://watch.example.com"));
DidWebVh.update(state, signer).changedParameters(delta).execute();

// Migrate a portable DID to a new domain:
DidWebVh.migrate(state, signer, "new.example.com")
        .newPath("dids:alice")
        .execute();

// Deactivate (permanent):
DidWebVh.deactivate(state, signer).execute();

// Each result carries the new entry / entries to append to did.jsonl:
for (var entry : rotated.getNewEntries()) {
    Files.writeString(
            Paths.get("did.jsonl"),
            entry.toJsonLine() + "\n",
            StandardOpenOption.APPEND);
}
```

### Pre-rotation (forward security)

Publish a hash of the next authorization key; rotation must reveal that key or the DID
becomes unrecoverable:

```java
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;

LocalKeySigner nextSigner = LocalKeySigner.generate();
String nextHash = PreRotationHashGenerator.generateHash(nextSigner.getPublicKeyMultikey());

DidWebVh.create("example.com", signer)
        .nextKeyHashes(List.of(nextHash))
        .execute();
```

On the next update, pass the previously-committed key as the current signer and supply a
new next-key hash the same way.

### Witness configuration

Witnesses co-sign each new log entry; their proofs live in `did-witness.json` next to
`did.jsonl` and MUST be published first (spec §3.7.8).

```java
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;

WitnessConfig witness = new WitnessConfig(
        2,  // threshold
        List.of(
                new WitnessEntry("did:key:z6MkWitness1..."),
                new WitnessEntry("did:key:z6MkWitness2...")));

DidWebVh.create("example.com", signer).witness(witness).execute();
```

Collecting witness proofs (sign `{"versionId":"<id>"}` with each authorized witness key and
write them to `did-witness.json`) is done outside `DidWebVh.update`; see
[`WizardWitnessProofs`](didwebvh-wizard/src/main/java/io/github/decentralized-identity/didwebvh/wizard/WizardWitnessProofs.java)
for a reference implementation that uses `ProofGenerator` and `WitnessProofEntry`.

### Publish a parallel `did:web` document

The spec (§3.7.10) lets a `did:webvh` publisher also serve a plain `did:web` document at
the same URL, so clients that do not understand `did:webvh` can still resolve the DID.

```java
import io.github.decentralizedidentity.didwebvh.core.didweb.DidWebPublisher;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;

DidDocument resolved = new DidResolver().resolveFromLog(jsonl, did).getDidDocument();

DidDocument webDoc = DidWebPublisher.toDidWeb(resolved);
String didWebUrl = DidWebPublisher.toDidWebUrl(did);  // did:webvh:... → did:web:...

Files.writeString(
        Paths.get("did.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(webDoc.asJsonObject()));
```

Publish `did.json` alongside `did.jsonl` (and `did-witness.json` if used).

## Pluggable Key Management

All signing goes through a single interface:

```java
public interface Signer {
    String keyType();              // e.g. "Ed25519VerificationKey2020"
    String verificationMethod();   // did:key:... identifier of the public key
    byte[] sign(byte[] data) throws SigningException;
}
```

Built-in implementations:

- `LocalKeySigner` (module `didwebvh-signing-local`) — Ed25519 keys from JSON key files.
  `LocalKeySigner.generate()`, `LocalKeySigner.fromJson(json)`, `signer.toJson()`.

A custom `Signer` only needs to return the DID-key verification method for its public key
and sign the bytes handed to it. Example skeleton for an HSM / cloud-KMS adapter:

```java
public final class KmsSigner implements Signer {
    private final String verificationMethod;  // pre-computed did:key from the HSM pubkey
    private final KmsClient client;

    @Override public String keyType() { return "Ed25519VerificationKey2020"; }
    @Override public String verificationMethod() { return verificationMethod; }
    @Override public byte[] sign(byte[] data) {
        return client.signEdDsa(data);  // must be a raw 64-byte Ed25519 signature
    }
}
```

Drop it into any `DidWebVh.create/update/migrate/deactivate` call — the library never
touches raw key material.

## Wizard CLI

Build the shaded (uber) jar, then run the interactive wizard:

```bash
./mvnw -pl didwebvh-wizard -am package
java -jar didwebvh-wizard/target/didwebvh-wizard.jar
```

(`-am` builds the `didwebvh-core` and `didwebvh-signing-local` dependencies first; the
jar is a self-contained uber-jar produced by `maven-shade-plugin` and is not published
to Maven Central.)

The wizard supports:

1. **Create** a new did:webvh DID (keys, pre-rotation, witnesses, watchers, TTL)
2. **Update** an existing DID — modify the document, change any parameter, migrate to a
   new domain (portable DIDs only), or deactivate; witness proofs are collected
   automatically when the active configuration requires them
3. **Resolve** a did:webvh DID (HTTPS or local file, with optional version filtering)
4. **Export** the parallel `did:web` document (`did.json`) for the current DID
5. Exit

Single-shot mode (skip the menu):

```bash
java -jar didwebvh-wizard.jar --action export --dir /srv/dids/alice
```

Valid `--action` values: `create`, `update`, `resolve`, `export`.

## Project Structure

```
didwebvh-java/
  didwebvh-core/           # Core library (model, create, resolve, update, didweb, validate)
  didwebvh-signing-local/  # Local key file signer adapter
  didwebvh-wizard/         # Interactive CLI wizard
```

## Building

```bash
./mvnw clean verify
```

Contributors should run the full build with JDK 21 for the closest local match to CI. The project supports
Java 11+, and CI also checks Java 11, 17, and 25, but SpotBugs is skipped on JDK 22+ in this repository.

## Running Tests

```bash
./mvnw test
```

## Specification

This library implements the [did:webvh DID Method v1.0](https://identity.foundation/didwebvh/v1.0/) specification.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md)
for the development workflow, and
[docs/AGENTS.md](docs/AGENTS.md) /
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for contributor (and AI agent)
guidelines and the technical design.

Before opening a PR, run:

```bash
./mvnw clean verify
```

This runs the full test suite, Checkstyle, SpotBugs, and JaCoCo coverage
checks across all modules.

## Security

If you believe you've found a security vulnerability, please follow the
instructions in [SECURITY.md](SECURITY.md) — **do not** open a public
GitHub issue.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). By contributing
to this project you agree to license your contributions under the same
terms.
