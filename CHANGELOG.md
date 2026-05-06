# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.1] - 2026-05-06

### Changed
- **Namespace migration**:
  Java package namespace migrated from
  `io.github.ivir3zam.*` to
  `io.github.decentralizedidentity.*` to reflect the new project
  ownership and align with the GitHub organization.
- **Maven coordinates update**:
  `groupId` changed from `io.github.ivir3zam` to
  `io.github.decentralizedidentity` across all modules to match the
  new namespace and publishing coordinates.
- **Repository migration**:
  project moved from a personal repository to the
  `decentralized-identity` GitHub organization; SCM metadata,
  documentation links, and issue tracking URLs updated accordingly.

### Fixed
- **Post-migration build issues**:
  resolved inconsistencies caused by outdated package imports and
  Maven coordinates after the namespace and repository migration.

## [0.2.0] - 2026-04-21

### Added
- **Wizard – Export parallel `did:web` document**: new menu option
  (also `--action export`) that resolves the local `did.jsonl` and
  writes a spec-compliant `did.json` via `DidWebPublisher.toDidWeb(...)`
  so publishers can serve a parallel `did:web` without leaving the CLI.
- **Core API – explicit controller support on create**:
  `CreateDidConfig.controllers(List<String>)` makes the DID Document
  `controller` property fully optional per DID Core §5.1.2. Passing
  `null` keeps the historical default (controller = the DID itself);
  an empty list omits the property; one or many entries emit string
  or array form respectively. The wizard exposes this via a new
  “Controller” prompt (blank = default, `-` = omit, comma list).
- **README**: end-to-end library usage guide covering create, resolve,
  update/migrate/deactivate, pre-rotation, witness configuration,
  parallel did:web export, and custom `Signer` implementations — so
  the library can be used standalone without the wizard.

### Changed
- **Wizard – update flow preserves existing state**:
  - Witness configure seeds from the active `WitnessConfig` so existing
    witnesses are kept and the threshold can span the full merged set.
    Adds an explicit “Remove an existing witness” option.
  - Watcher update shows the current list and **appends** new entries
    by default (comma-separated), with `clear` to wipe. Previously the
    input silently **replaced** the list, dropping existing watchers.
- **Wizard – witness proofs**: Create and Update now auto-sign with
  **every** stored witness secret that matches the authorized set,
  rather than stopping at the threshold. Threshold is a lower bound
  for prompting, not an upper bound for signing.
- **Wizard – shaded jar naming**: the CLI uber-jar is now
  `didwebvh-wizard/target/didwebvh-wizard.jar` (stable name, no
  version, no classifier). The wizard is excluded from Maven Central
  deploy, so replacing the thin jar with the shaded jar is safe.

### Fixed
- **Create with witnesses left the first entry unpublishable**: when
  a witness configuration was active on the very first log entry, no
  `did-witness.json` was produced, so spec-compliant resolvers failed
  with *“Witness proofs are required but were not provided.”* The
  Create wizard now collects witness proofs for the first entry and
  writes `did-witness.json` before `did.jsonl` (spec §3.7.8 ordering).
- **Export wizard failed on witnessed DIDs**: `DidResolver.resolveFromLog`
  requires a witness-proof collection whenever the active config has
  witnesses. Export now uses `DidWebVhState.validate()` for chain
  integrity and takes the DID Document directly from the latest log
  entry, so export works on witnessed DIDs without an in-memory
  `did-witness.json` hand-off.
- **Witness menu numbering collision**: the “Current witnesses” list
  no longer shares indices with the action menu — the current set is
  rendered as bullet points with a size header, and the “Remove”
  sub-step re-prints numbered options when an index is requested.

## [0.1.0] - 2026-04-20

Initial public release of `didwebvh-java`, a Java 11+ implementation of the
[did:webvh v1.0](https://identity.foundation/didwebvh/v1.0/) DID method.

### Added
- **Core API** (`didwebvh-core`):
  - `DidWebVh.create(domain, signer)` — DID creation with SCID generation,
    authorization keys, `eddsa-jcs-2022` Data Integrity proofs, optional
    pre-rotation, witness configuration, `portable`, `ttl`, `watchers`,
    `alsoKnownAs`, and arbitrary `additionalDocumentContent`.
  - `DidWebVh.update(state, signer)` — standard DID update (document
    replacement, parameter rotation, key rotation).
  - `DidWebVh.migrate(state, signer, newDomain)` — portable-DID migration
    to a new domain while preserving the SCID and the full log chain.
  - `DidWebVh.deactivate(state, signer)` — DID deactivation per spec §3.6.4.
  - `DidWebVh.resolve(did)` — HTTPS resolution, JSONL log fetch, optional
    witness-proof fetch, full chain validation.
  - `DidWebVh.validate(entries, expectedDid)` — offline log-chain validation.
  - `LogEntry` / `DidWebVhState` / `ResolveResult` model types with Gson
    serialization and JCS canonicalization.
  - `DidUrlParser` and DID-to-HTTPS transformation (spec §3.4), plus
    `toDidWebUrl()` for parallel `did:web` lookup.
  - `DidWebPublisher` — parallel `did:web` document emission.
- **Signing SPI** (`didwebvh-core`) — pluggable `Signer` interface;
  built-in `Ed25519Suite` / JCS hashing utilities.
- **Local-key adapter** (`didwebvh-signing-local`) — `LocalKeySigner` that
  loads Ed25519 keys from JSON key files.
- **Interactive wizard** (`didwebvh-wizard`) — picocli + JLine CLI for
  guided create, update (modify / migrate / deactivate), and resolve
  flows; shipped as a shaded uber-jar on the GitHub Release.
- **Test vectors** under `didwebvh-core/src/test/resources/test-vectors/`
  (first-log-entry-good / tampered, multi-entry + witness, deactivated,
  migrated, pre-rotation) and `SpecComplianceIT` covering 18 spec MUSTs.
- **CI** — GitHub Actions matrix across JDK 11, 17, 21, and 25, with
  Checkstyle, SpotBugs, JaCoCo, and Codecov upload.
- **Release pipeline** — `release` Maven profile and
  `.github/workflows/release.yml` that publishes to Sonatype Central on
  tag push (`v*`) and attaches JARs to the GitHub Release.

[Unreleased]: https://github.com/decentralized-identity/didwebvh-java/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/decentralized-identity/didwebvh-java/releases/tag/v0.1.0
