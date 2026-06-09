# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- **Witness-list updates witnessed by the wrong list** (spec §3.7.5,
  issue #6): per the spec a replacement `witness` list only becomes
  active *after* the entry that introduces it is published, so the
  entry making the change must be witnessed by the list in effect
  *before* it. Both the proof generator (`WizardWitnessProofs`) and
  the resolver (`WitnessValidator`) were instead using the new
  (merged) list, so reducing a list (e.g. two witnesses down to one)
  produced and required only a single proof. Now an entry is
  witnessed by the prior active list whenever witnessing was already
  active — covering list changes and turning witnessing off — with
  the new list applying only on first activation. This matches the
  Rust reference output for `vectors/witness-update`.

## [0.3.0] - 2026-05-29

This release closes the failures reported by the
[did:webvh interop test suite](https://github.com/swcurran/didwebvh-test-suite)
against the v0.2.0 line (see issue #2).

### Added
- **Negative interop test vectors** vendored from the upstream
  did:webvh test suite under
  `didwebvh-core/src/test/resources/interop/`
  (`basic-create/python`, `basic-update/ts`, full `java-eecc` log
  set, `pre-rotation-consume/{rust,java-eecc}`,
  `witness-update/rust`, `witness-threshold/rust`, and
  `negative-cross-did-witness-replay/ts`), each exercised by a
  dedicated JUnit test under `didwebvh-core/.../interop/`.
- **Implicit `#files` and `#whois` services in resolved DID
  Documents** (spec §3.8 and §3.9): the resolver now emits a
  `relativeRef` `#files` service and a
  `LinkedVerifiablePresentation` `#whois` service unless the
  controller has already declared services with the same id. The
  shared logic lives in `didweb.ImplicitServices` and is also
  reused by `DidWebPublisher`.

### Fixed
- **Cross-DID witness-proof replay** (spec §3.7.5, lines 884-889):
  when the `witness` parameter is set to `{}` while witnesses were
  active, the transition entry MUST itself be witnessed by the
  prior witnesses. `WitnessValidator` was merging the empty config
  in first and skipping the entry as inactive, which let an
  attacker disable witnessing and replay a stale (or cross-DID)
  proof for an earlier entry. The validator now tracks the prior
  config and, on a witness-off transition, requires approval from
  the prior witnesses.
- **`witness: {}` round-trip across implementations**: Python and
  TS serialise an empty `"witness": {}` object in parameters when
  no witnesses are configured. Gson was instantiating
  `WitnessConfig` via `Unsafe.allocateInstance`, bypassing the
  constructor and leaving `witnesses` null — every call to
  `isActive()` / `getWitnesses()` then NPE'd on the first
  Python/TS log entry. Java was also re-serialising the empty
  config as `{"threshold":0,"witnesses":[]}` instead of `{}`,
  producing a different JCS canonical form for SCID, entry-hash
  and proof computation. Added a no-arg constructor (so Gson uses
  `Constructor.newInstance`) and a `WitnessConfigTypeAdapter` that
  round-trips the empty-object form.
- **Pre-rotation entries authorized against the wrong key set**
  (spec §3.7.5): when the previous entry committed a
  `nextKeyHashes`, the active updateKeys for the current entry are
  the current entry's own `updateKeys`, not the previous entry's.
  `LogChainValidator` was unconditionally using the previous
  entry, failing every `pre-rotation-consume` log from rust and
  java-eecc. `DeactivateDidOperation` made the same mistake when
  emitting its intermediate pre-rotation-consuming entry; it now
  signs that entry with `nextRotationSigner`. Regenerated
  `pre-rotation-log.jsonl` under the corrected rules.
- **Witness-proof pruning and bare-multikey witness ids** (spec
  §3.7.8): a witness proof at versionId V implicitly approves all
  prior log entries, and the DID Controller SHOULD prune
  `did-witness.json` to retain only the latest proof per witness.
  `WitnessValidator` required an exact-versionId proof per entry
  and failed with "missing witness proof" against the Rust pruned
  files. It also compared witness ids as `did:key:<multikey>`
  against the Rust implementation's bare-multikey form, yielding 0
  authorized proofs. The validator now pre-verifies all proofs
  once, counts distinct authorized witnesses per entry, and
  matches witness ids by multikey portion to accept both
  `did:key:z6Mk…` and bare `z6Mk…` forms.
- **Pre-rotation and portable-SCID negative-test gaps** closed for
  `negative-pre-rotation-omit-updatekeys` and
  `negative-portable-scid-swap`.
- **Release auth**: auto-detect and decode base64-encoded
  `user:pass` `OSSRH_TOKEN` values so Sonatype publishing succeeds
  with either token form.

### Changed
- **CI**: GitHub Actions upgraded to versions compatible with
  Node.js 24, and Node.js 24 is forced in both the CI and release
  workflows to silence the deprecation warning emitted by older
  actions on the GHA runners.
- **Release notes**: the GitHub Release body is now generated
  directly from the matching `## [VERSION]` section of this
  CHANGELOG (with an appended Maven Central coordinate), and the
  release attaches only the self-contained `didwebvh-wizard.jar`
  uber-jar — library modules are consumed from Maven Central.
- **Test coverage**: raised `didwebvh-core` from ~77% to ~82% on
  the Codecov metric (line coverage 93%) by covering real branches
  in `LogChainValidator`, `LogProcessor`, `DidResolver`,
  `MigrateDidOperation`, `CreateDidOperation`, `DidWebVhUrl`, and
  the file fetcher — malformed versionId/versionTime, future
  versionTime rejection, witness threshold bounds, method-version
  downgrade, query-param parsing, PROACTIVE / WHEN_REQUIRED witness
  fetch branches, migrate guards (null/empty inputs, deactivated,
  newPath, alsoKnownAs dedup), controller-list array vs string
  forms, and URL port / IPv6 / empty-domain rejection.

## [0.2.0] - 2026-05-06

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
- **Project relocation to `decentralized-identity` GitHub
  organization**: Java package namespace migrated from
  `io.github.ivir3zam.*` to `io.github.decentralizedidentity.*` and
  Maven `groupId` changed from `io.github.ivir3zam` to
  `io.github.decentralized-identity` across all modules. SCM
  metadata, documentation links, and issue tracking URLs updated
  accordingly.
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

[Unreleased]: https://github.com/decentralized-identity/didwebvh-java/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/decentralized-identity/didwebvh-java/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/decentralized-identity/didwebvh-java/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/decentralized-identity/didwebvh-java/releases/tag/v0.1.0
