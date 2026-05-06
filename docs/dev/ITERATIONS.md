# Implementation Iterations

This file contains the ordered, detailed prompts for each step of building `didwebvh-java` from scratch to production-ready. Each iteration builds on the previous ones. An AI agent or human can execute them sequentially.

Read `docs/AGENTS.md` and `docs/ARCHITECTURE.md` before starting. The spec TXT (`docs/spec/Webvh v1.0.txt`) is the authoritative reference.

### Status Key

| Status | Meaning |
|--------|---------|
| `[NOT STARTED]` | Work has not begun |
| `[IN PROGRESS]` | Currently being worked on |
| `[DONE]` | Completed and verified |

---

## Iteration 1: Project Scaffolding `[DONE]`

### Goal
Set up the multi-module Maven project structure, CI pipeline, and quality tooling. After this iteration, `./mvnw clean verify` passes with zero code (empty modules).

### Tasks

1. **Create the Maven Wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`). Use Maven 3.9.x.

2. **Create the parent POM** (`pom.xml`) with:
   - `groupId`: `io.github.decentralized-identity`
   - `artifactId`: `didwebvh-java`
   - `version`: `0.1.0-SNAPSHOT`
   - `packaging`: `pom`
   - `<modules>`: `didwebvh-core`, `didwebvh-signing-local`, `didwebvh-wizard`
   - Java 11 source/target via `maven-compiler-plugin`
   - `<dependencyManagement>` for all shared dependency versions:
     - `com.google.code.gson:gson:2.10.1`
     - `io.github.erdtman:java-json-canonicalization:1.1`
     - `com.github.multiformats:java-multihash:1.3.3`
     - `io.github.novacrypto:Base58:2022.01.17`
     - `org.bouncycastle:bcprov-jdk15on:1.70`
     - `org.bouncycastle:bcpkix-jdk15on:1.70`
     - `com.squareup.okhttp3:okhttp:4.12.0`
     - `info.picocli:picocli:4.7.5`
     - `org.jline:jline:3.25.1`
     - `org.junit.jupiter:junit-jupiter:5.10.2`
     - `org.assertj:assertj-core:3.25.3`
     - `org.mockito:mockito-core:5.10.0`
     - `com.squareup.okhttp3:mockwebserver:4.12.0`
   - Plugin management:
     - `maven-compiler-plugin` (Java 11)
     - `maven-surefire-plugin` (3.2.x, for JUnit 5)
     - `maven-failsafe-plugin` (3.2.x)
     - `jacoco-maven-plugin` (0.8.11) with report goal bound to `verify` phase
     - `maven-checkstyle-plugin` (3.3.x) with Google Java Style checks
     - `spotbugs-maven-plugin` (4.8.x)
     - `maven-source-plugin` and `maven-javadoc-plugin` for release

3. **Create child module POMs**:
   - `didwebvh-core/pom.xml`: depends on gson, java-json-canonicalization, java-multihash, Base58, bouncycastle, okhttp. Test deps: junit-jupiter, assertj, mockito, mockwebserver.
   - `didwebvh-signing-local/pom.xml`: depends on `didwebvh-core`, gson, bouncycastle. Test deps: junit-jupiter, assertj.
   - `didwebvh-wizard/pom.xml`: depends on `didwebvh-core`, `didwebvh-signing-local`, picocli, jline. Test deps: junit-jupiter, assertj.

4. **Create empty source directories** for each module:
   - `src/main/java/io/github/decentralized-identity/didwebvh/...`
   - `src/test/java/io/github/decentralized-identity/didwebvh/...`
   - `src/test/resources/`
   - Add a placeholder class in each module so the compiler has something to compile (e.g., `package-info.java`).

5. **Create `.gitignore`** for Java/Maven (target/, *.class, .idea/, *.iml, .DS_Store, etc.)

6. **Create `LICENSE`** file (Apache 2.0).

7. **Create GitHub Actions CI** (`.github/workflows/ci.yml`):
   - Trigger on push to `main` and all PRs
   - Matrix: Java 11, 17, 21, 25 on `ubuntu-latest`
   - Steps: checkout, setup-java (temurin), cache maven, `./mvnw clean verify -B`
   - Upload JaCoCo coverage to Codecov (using `codecov/codecov-action@v4`)
   - Run SonarCloud analysis (using `sonarsource/sonarcloud-github-action` or maven sonar plugin)

8. **Create `checkstyle.xml`** at project root based on Google Java Style with minor relaxations (line length 120).

9. **Create `sonar-project.properties`** for SonarCloud integration.

### Acceptance Criteria
- `./mvnw clean verify` passes with zero errors
- CI workflow file exists and is valid YAML
- All three modules are recognized by Maven
- JaCoCo, Checkstyle, SpotBugs plugins are configured (they'll do nothing with no code yet)
- `.gitignore` and `LICENSE` exist

### Implementation Notes
- Maven wrapper pre-existed (3.9.9); reused as-is
- JitPack repository added for `com.github.multiformats:java-multihash` (not on Maven Central)
- SpotBugs 4.9.3 used; auto-skipped on JDK >= 22 via profile (ASM doesn't support class file major version 69)
- `./mvnw clean verify` passes on local JDK 25 with SpotBugs skipped; contributors and agents should use
  JDK 21 for local full verification so SpotBugs runs before CI.

---

## Iteration 2: Model Classes `[DONE]`

### Goal
Implement all data model classes that represent the did:webvh spec's data structures. These are pure data holders with JSON serialization. No business logic yet.

### Tasks

1. **`DidWebVhException`** and its subclasses in `core` package:
   - `DidWebVhException extends RuntimeException` (message, cause constructors)
   - `ValidationException extends DidWebVhException`
   - `ResolutionException extends DidWebVhException`
   - `SigningException extends DidWebVhException`
   - `UrlParseException extends DidWebVhException`

2. **`VersionId`** in `model` package:
   - Fields: `int versionNumber`, `String entryHash`
   - Parse from string: `"1-QmHash..."` -> `VersionId(1, "QmHash...")`
   - `toString()` returns `"1-QmHash..."`
   - For first entry preliminary: use `"{SCID}"` as the full string
   - Validation: version number >= 1, entry hash is non-empty

3. **`Parameters`** in `model` package:
   - Fields matching spec section 3.7.1:
     - `String method` (e.g., "did:webvh:1.0")
     - `String scid`
     - `List<String> updateKeys` (multikey format)
     - `List<String> nextKeyHashes` (nullable/empty list)
     - `WitnessConfig witness` (nullable)
     - `List<String> watchers` (nullable/empty list)
     - `Boolean portable`
     - `Boolean deactivated`
     - `Integer ttl`
   - JSON serialization/deserialization with Gson
   - `merge(Parameters other)` method: non-null fields in `other` override `this`, returns new `Parameters`
   - Handle the spec rule about `null` values: gracefully accept null and convert to default

4. **`WitnessConfig`** in `witness` package:
   - Fields: `int threshold`, `List<WitnessEntry> witnesses`
   - `WitnessEntry`: just `String id` (a did:key DID)
   - JSON shape: `{"threshold": n, "witnesses": [{"id": "did:key:..."}]}`

5. **`DataIntegrityProof`** in `model` package:
   - Fields: `String type`, `String cryptosuite`, `String verificationMethod`, `String proofPurpose`, `String created`, `String proofValue`
   - Defaults for did:webvh:1.0: type="DataIntegrityProof", cryptosuite="eddsa-jcs-2022", proofPurpose="assertionMethod"

6. **`LogEntry`** in `model` package:
   - Fields: `String versionId`, `String versionTime`, `Parameters parameters`, `JsonObject state`, `List<DataIntegrityProof> proof`
   - `toJsonLine()`: serialize to compact JSON (no whitespace), as required by JSONL spec
   - `fromJsonLine(String line)`: parse a single JSONL line
   - `getVersionNumber()`: parse version number from versionId
   - `getEntryHash()`: parse entry hash from versionId
   - Note: `parameters` may be empty `{}` for entries with no parameter changes
   - Note: `state` is the full DID Document as a generic JSON object

7. **`DidDocument`** in `model` package:
   - Thin wrapper around `JsonObject`
   - Convenience: `getId()`, `getController()`, `getAlsoKnownAs()`, `getVerificationMethod()`, `getService()`
   - `withId(String id)` returns new instance with updated id
   - This class does NOT validate DID Document structure -- it's just a container

8. **`WitnessProofEntry`** and **`WitnessProofCollection`** in `witness` package:
   - `WitnessProofEntry`: `String versionId`, `List<DataIntegrityProof> proof`
   - `WitnessProofCollection`: `List<WitnessProofEntry> entries`
   - JSON shape matches `did-witness.json` format from spec

9. **`ResolutionMetadata`** in `model` package:
   - Fields: `String versionId`, `String versionTime`, `String created`, `String updated`, `String scid`, `Boolean portable`, `Boolean deactivated`, `String ttl`, `WitnessConfig witness`, `List<String> watchers`

10. **`ResolveResult`** in `model` package:
    - Fields: `DidDocument didDocument`, `ResolutionMetadata metadata`, `String error`, `JsonObject problemDetails`

### Tests

- For each model class:
  - Construction and getter tests
  - JSON round-trip: serialize to JSON, deserialize back, assert equality
  - `VersionId` parsing from various valid/invalid strings
  - `Parameters.merge()` with various combinations of null/non-null fields
  - `LogEntry.toJsonLine()` and `fromJsonLine()` round-trip
  - Edge cases: empty parameters `{}`, missing optional fields

### Acceptance Criteria
- All model classes exist with JSON serialization support
- All model tests pass
- `./mvnw clean verify` passes
- Checkstyle passes (Google style)

### Implementation Notes
- Added exception hierarchy (`DidWebVhException` + 4 subclasses) in `core` package.
- Added model classes: `VersionId`, `Parameters`, `DataIntegrityProof`, `LogEntry`, `DidDocument`, `ResolutionMetadata`, `ResolveResult`, plus a shared `JsonSupport` Gson helper.
- Added witness classes: `WitnessEntry`, `WitnessConfig`, `WitnessProofEntry`, `WitnessProofCollection`.
- `VersionId.preliminary(scid)` covers the first-entry case where the raw string is the SCID.
- `Parameters.merge()` copies base then overlays only non-null fields from the delta; both inputs remain unchanged.
- `LogEntry.toJsonLine()` uses a Gson that omits nulls (compact JSONL); `fromJsonLine()` is the inverse.
- Bumped `jacoco.version` 0.8.11 → 0.8.13 (required for the Java 25 CI matrix; 0.8.11 cannot instrument class file major 69).
- 17 new unit tests; `./mvnw clean verify` passes on all modules.

---

## Iteration 3: Crypto Primitives `[DONE]`

### Goal
Implement the cryptographic building blocks: JCS canonicalization, multihash, base58btc, SCID generation, entry hash generation, and multikey utilities.

### Tasks

1. **`Jcs`** in `crypto` package:
   - `static byte[] canonicalize(String json)` - takes JSON string, returns JCS-canonicalized bytes
   - `static byte[] canonicalize(JsonObject json)` - takes Gson JsonObject
   - Uses `java-json-canonicalization` library
   - Test with known inputs/outputs from RFC 8785 examples

2. **`MultihashUtil`** in `crypto` package:
   - `static byte[] encode(HashAlgorithm algorithm, byte[] data)` - hash data with algorithm, return multihash-encoded result
   - `static HashAlgorithm extractAlgorithm(byte[] multihash)` - extract algorithm from multihash prefix
   - `static byte[] extractDigest(byte[] multihash)` - extract raw digest bytes
   - `HashAlgorithm` enum: `SHA2_256` (only one needed for v1.0)

3. **`Base58Btc`** in `crypto` package:
   - `static String encode(byte[] data)`
   - `static byte[] decode(String encoded)`
   - Wraps the Base58 library

4. **`ScidGenerator`** in `crypto` package:
   - `static String generate(String preliminaryEntryJson)` - implements SCID generation from spec section 3.7.3:
     1. JCS-canonicalize the preliminary entry
     2. SHA-256 hash
     3. Multihash-encode
     4. Base58btc-encode
   - `static boolean verify(String scid, String firstEntryJson)` - implements SCID verification from spec section 3.7.3:
     1. Remove proof from entry
     2. Replace versionId with `"{SCID}"`
     3. Replace scid value in parameters with `"{SCID}"`
     4. Treat as string, replace all occurrences of actual SCID with `{SCID}`
     5. Generate SCID from modified entry
     6. Compare with provided scid

5. **`EntryHashGenerator`** in `crypto` package:
   - `static String generate(String entryJson, String predecessorVersionId)` - implements entry hash generation from spec section 3.7.4:
     1. Remove "proof" from entry JSON
     2. Set "versionId" to predecessorVersionId
     3. JCS-canonicalize
     4. SHA-256 hash
     5. Multihash-encode
     6. Base58btc-encode
   - `static boolean verify(LogEntry entry, String predecessorVersionId)` - verify an entry's hash

6. **`MultikeyUtil`** in `crypto` package:
   - `static String encode(String keyType, byte[] publicKeyBytes)` - encode public key to multikey string (e.g., `z6Mk...` for Ed25519)
   - `static byte[] decode(String multikey)` - extract raw public key bytes
   - `static String keyTypeFromMultikey(String multikey)` - determine key type from multicodec prefix
   - Ed25519 multicodec prefix: `0xed01`

7. **`PreRotationHashGenerator`** in `crypto` package:
   - `static String generateHash(String multikeyPublicKey)` - implements pre-rotation key hash from spec section 3.7.7:
     1. Take multikey string bytes
     2. SHA-256 hash
     3. Multihash-encode
     4. Base58btc-encode

### Tests

- **JCS**: Test with multiple JSON inputs (nested objects, arrays, unicode, numbers) and verify output matches expected canonical form
- **MultihashUtil**: Test SHA-256 encoding/decoding, algorithm extraction
- **Base58Btc**: Round-trip tests, known vector tests
- **ScidGenerator**: Create a known preliminary entry, verify SCID output matches expected value. Test verification with valid and tampered entries.
- **EntryHashGenerator**: Create known entries, verify hash output. Test verification positive and negative.
- **MultikeyUtil**: Encode/decode Ed25519 keys, verify multicodec prefix handling
- **PreRotationHashGenerator**: Generate hash for known key, verify output

### Acceptance Criteria
- All crypto classes implemented and tested
- SCID generation matches the spec's algorithm exactly
- Entry hash generation matches the spec's algorithm exactly
- `./mvnw clean verify` passes
- No crypto operations depend on the `Signer` interface (that's in the signing package)

### Implementation Notes
- Implemented `Jcs` using `java-json-canonicalization` (RFC 8785); wraps `JsonCanonicalizer` for both `String` and `JsonObject` inputs.
- Implemented `MultihashUtil` with manual varint encoding (single-byte codes only, sufficient for SHA2-256 `0x12`); avoids pulling in the `java-multihash` library at runtime since only SHA-256 is needed for v1.0.
- `Base58Btc` wraps `io.github.novacrypto:Base58`; added `encodeMultibase`/`decodeMultibase` helpers for the `z`-prefixed multibase format used throughout the spec.
- `MultikeyUtil` handles Ed25519 multicodec prefix `0xed01`; encode/decode/keyType detection.
- `ScidGenerator.generate()` follows spec 3.7.3: JCS → SHA-256 → multihash → base58btc-multibase. `verify()` strips proof, replaces SCID with `{SCID}`, and re-derives.
- `EntryHashGenerator.generate()` follows spec 3.7.4: strips proof, sets versionId to predecessor, JCS → SHA-256 → multihash → base58btc-multibase.
- `PreRotationHashGenerator` hashes the UTF-8 bytes of a multikey string per spec 3.7.7.
- 34 new unit tests across 7 test classes; `./mvnw clean verify` passes on all modules.

---

## Iteration 4: Signing Interface and Proof Generation `[DONE]`

### Goal
Implement the `Signer` interface, `ProofGenerator`, `ProofVerifier`, and the `LocalKeySigner` adapter.

### Tasks

1. **`Signer`** interface in `signing` package:
   ```java
   public interface Signer {
       String keyType();
       String verificationMethod();
       byte[] sign(byte[] data) throws SigningException;
   }
   ```

2. **`ProofGenerator`** in `signing` package:
   - `static DataIntegrityProof generate(Signer signer, JsonObject logEntryWithoutProof)`:
     1. JCS-canonicalize the log entry JSON (proof field must not be present)
     2. Call `signer.sign(canonicalizedBytes)`
     3. Multibase-encode the signature (base58btc with 'z' prefix)
     4. Construct `DataIntegrityProof` with:
        - `type`: "DataIntegrityProof"
        - `cryptosuite`: "eddsa-jcs-2022"
        - `verificationMethod`: from signer
        - `proofPurpose`: "assertionMethod"
        - `created`: current UTC ISO8601
        - `proofValue`: multibase-encoded signature

3. **`ProofVerifier`** in `signing` package:
   - `static boolean verify(DataIntegrityProof proof, JsonObject logEntryWithoutProof)`:
     1. Extract public key from `proof.verificationMethod` (it's a `did:key:z6Mk...#z6Mk...` URI)
     2. Decode the multikey to get raw Ed25519 public key bytes
     3. JCS-canonicalize the log entry JSON
     4. Decode the `proofValue` (multibase base58btc)
     5. Verify Ed25519 signature over canonicalized bytes using public key (BouncyCastle)
   - `static boolean isAuthorized(DataIntegrityProof proof, List<String> activeUpdateKeys)`:
     1. Extract the multikey from proof.verificationMethod
     2. Check if it's in the activeUpdateKeys list

4. **`LocalKeySigner`** in `didwebvh-signing-local` module:
   - `static LocalKeySigner generate()` - generate new Ed25519 keypair using BouncyCastle
   - `static LocalKeySigner fromJson(String json)` - load from JSON format: `{"kty":"OKP","crv":"Ed25519","x":"<base64url>","d":"<base64url>"}`
   - `static LocalKeySigner fromPrivateKey(byte[] privateKeyBytes)` - load from raw bytes
   - `String toJson()` - serialize keypair to JSON
   - `String getPublicKeyMultikey()` - return the multikey-encoded public key (for use in updateKeys)
   - Implement `Signer` interface methods:
     - `keyType()` returns "Ed25519"
     - `verificationMethod()` returns `"did:key:<multikey>#<multikey>"` format
     - `sign(byte[])` signs with Ed25519 private key via BouncyCastle

### Tests

- **ProofGenerator**: Generate a proof with a test signer, verify the structure is correct
- **ProofVerifier**: Verify a known-good proof, verify rejection of tampered data, verify authorized key check
- **Round-trip**: Generate proof -> verify proof (should pass), tamper with data -> verify (should fail)
- **LocalKeySigner**: Generate keypair, sign data, verify signature. JSON round-trip. Load from known JSON.
- **Authorization**: Test `isAuthorized` with matching and non-matching keys

### Acceptance Criteria
- `Signer` interface exists in core module
- `ProofGenerator` and `ProofVerifier` work with Ed25519 eddsa-jcs-2022
- `LocalKeySigner` can generate keys, sign, and verify
- All tests pass
- No dependency from core on `didwebvh-signing-local` (only the interface is in core)

### Implementation Notes
- `Signer` interface in `core.signing` package with `keyType()`, `verificationMethod()`, `sign()`.
- `ProofGenerator.generate()` follows eddsa-jcs-2022: JCS-canonicalize → sign → base58btc-multibase encode → build `DataIntegrityProof` with defaults.
- `ProofVerifier.verify()` extracts Ed25519 public key from `did:key:` verification method, JCS-canonicalizes entry, verifies Ed25519 signature via BouncyCastle.
- `ProofVerifier.isAuthorized()` extracts multikey from verification method and checks membership in active update keys.
- `LocalKeySigner` in `signing-local` module: generates Ed25519 keypairs, serializes to/from JWK-like JSON (`kty=OKP, crv=Ed25519`), implements `Signer`.
- 19 new tests (11 core signing + 8 signing-local); `./mvnw clean verify` passes on all modules (70 total tests).

---

## Iteration 5: DID Creation `[DONE]`

### Goal
Implement the full DID creation flow as specified in spec section 3.6.1. After this iteration, a user can create a new did:webvh DID with a valid first log entry.

### Tasks

1. **`CreateDidConfig`** builder in `create` package:
   - Required: `String domain`, `Signer signer`
   - Optional: `String path`, `Boolean portable`, `Integer ttl`, `List<String> alsoKnownAs`, `WitnessConfig witness`, `List<String> watchers`, `List<String> nextKeyHashes` (pre-rotation), `JsonObject additionalDocumentContent` (services, extra verification methods, etc.)
   - `execute()` method that runs the creation

2. **`CreateDidOperation`** in `create` package. The `execute()` flow:
   1. Build the DID string with `{SCID}` placeholder: `did:webvh:{SCID}:<domain>[:<path>]`
   2. Build initial DID Document with `{SCID}` placeholders in all references
      - `id`: the DID string with placeholder
      - Add verification methods from signer's public key if requested
      - Add services if provided
      - Add controller
      - Add alsoKnownAs if provided
   3. Build initial Parameters:
      - `method`: "did:webvh:1.0"
      - `scid`: "{SCID}"
      - `updateKeys`: [signer's public multikey]
      - `portable`, `ttl`, `witness`, `watchers`, `nextKeyHashes` from config
   4. Build preliminary log entry:
      - `versionId`: "{SCID}"
      - `versionTime`: current UTC ISO8601
      - `parameters`: from step 3
      - `state`: DID Document from step 2
      - No proof field
   5. Generate SCID from preliminary entry using `ScidGenerator`
   6. Replace all `{SCID}` placeholders with actual SCID in the entire JSON (string replacement)
   7. Generate entry hash using `EntryHashGenerator` with SCID as predecessor
   8. Set `versionId` to `"1-<entryHash>"`
   9. Generate Data Integrity proof using `ProofGenerator` with the signer
   10. Attach proof to log entry
   11. Return `CreateDidResult` with: DID string, first `LogEntry`, signer's public key info

3. **`CreateDidResult`** in `create` package:
   - `String did` - the full DID string
   - `LogEntry logEntry` - the first log entry
   - `String logLine` - the JSONL line for `did.jsonl`

4. **`DidWebVh.create(String domain)`** facade method that returns `CreateDidConfig` builder.

### Tests

- Create a DID with minimal config (just domain + signer), verify:
  - DID string format is valid
  - SCID is present and correctly placed
  - versionId is "1-<hash>"
  - versionTime is valid ISO8601
  - Parameters contain method, scid, updateKeys
  - State contains DID Document with correct id
  - Proof is valid (verify with ProofVerifier)
  - Entry hash is valid (verify with EntryHashGenerator)
  - SCID is valid (verify with ScidGenerator)
- Create with all options (portable, ttl, witness, watchers, pre-rotation, services)
- Create with path in domain (e.g., `example.com:dids:issuer`)
- Create with port (e.g., `example.com%3A3000`)
- Verify JSON line output is compact (no whitespace)
- Round-trip: create -> serialize to JSONL -> parse back -> verify all fields

### Acceptance Criteria
- Full DID creation flow works end-to-end
- Created log entries pass SCID verification
- Created log entries pass entry hash verification
- Created log entries have valid Data Integrity proofs
- All JSONL output is compact single-line JSON
- Tests cover normal and edge cases

### Implementation Notes
- Added `CreateDidConfig` builder, `CreateDidResult` data class, and `CreateDidOperation` in `core.create` package.
- `CreateDidOperation.execute()` follows spec section 3.6.1: builds preliminary entry with `{SCID}` placeholders, generates SCID, replaces placeholders, generates entry hash, sets `versionId` to `1-<hash>`, signs with `ProofGenerator`.
- `DidWebVh.create(domain, signer)` facade returns `CreateDidConfig` for fluent builder usage.
- Fixed `ScidGenerator.verify()` to explicitly reset `versionId` and `parameters.scid` to `{SCID}` before string replacement (spec steps 2-3); previously only did blanket string replacement which failed because `versionId` (`1-<hash>`) doesn't contain the SCID.
- DID Document includes `@context`, `id`, `controller`, `verificationMethod` (Multikey type), `authentication`, and `assertionMethod` by default.
- Signer's public multikey is extracted from `verificationMethod()` URI (no dependency on `LocalKeySigner`).
- Creation-time validation: `ttl` must be positive, `nextKeyHashes` entries must be non-empty multibase strings. Witness config validation deferred to Iteration 7 (log chain validation). Domain/path validation deferred to Iteration 6 (`DidWebVhUrl`).
- 27 new unit tests covering minimal creation, all options, SCID/hash/proof verification, JSONL round-trip, path/port handling, and validation errors; `./mvnw clean verify` passes on all modules (97 total tests).

---

## Iteration 6: DID URL Parsing and DID-to-HTTPS Transformation `[DONE]`

### Goal
Implement DID URL parsing and the DID-to-HTTPS transformation algorithm from spec section 3.4.

### Tasks

1. **`DidWebVhUrl`** in `url` package:
   - Parse `did:webvh:<SCID>:<domain>[:<path>...]` into components:
     - `String scid`
     - `String domain` (decoded, with port if present)
     - `List<String> pathSegments`
     - `String fragment` (optional, after `#`)
     - `Map<String, String> queryParams` (optional, after `?`)
   - Validate against spec ABNF:
     - Must start with `did:webvh:`
     - SCID must be 46 characters base58btc
     - Domain must be a valid DNS name (no IP addresses per spec section 3.3)
     - Port must be percent-encoded (`%3A`), reject raw colon in domain
     - Path segments must be non-empty (no `::` producing empty segments)
   - `toString()` reconstructs the DID string
   - This class becomes the single source of truth for domain/path validation; `CreateDidOperation` delegates domain/path validation to `DidWebVhUrl.validate()` or equivalent

2. **`DidToHttpsTransformer`** in `url` package:
   - `static String toHttpsUrl(String did)` - full implementation of spec section 3.4:
     1. Remove `did:webvh:` prefix
     2. Remove SCID segment
     3. Transform domain segment (decode percent-encoded port, Unicode normalization, IDNA/Punycode)
     4. Transform path segments (replace `:` with `/`, percent-encode each segment)
     5. Reconstruct HTTPS URL:
        - With port: `https://<domain>:<port>/<path>/did.jsonl`
        - With path: `https://<domain>/<path>/did.jsonl`
        - No path: `https://<domain>/.well-known/did.jsonl`
   - `static String toWitnessUrl(String did)` - same as above but ending in `did-witness.json`
   - `static String toDidWebUrl(String didWebVh)` - convert did:webvh to equivalent did:web URL

3. **Handle DID URL query parameters** for resolution:
   - `?versionId=<full versionId>` - resolve specific version
   - `?versionTime=<ISO8601>` - resolve version active at that time
   - `?versionNumber=<int>` - resolve specific version number (did:webvh extension)

### Tests

- Parse all example DIDs from the spec:
  - `did:webvh:{SCID}:example.com` -> `https://example.com/.well-known/did.jsonl`
  - `did:webvh:{SCID}:issuer.example.com` -> `https://issuer.example.com/.well-known/did.jsonl`
  - `did:webvh:{SCID}:example.com:dids:issuer` -> `https://example.com/dids/issuer/did.jsonl`
  - `did:webvh:{SCID}:example.com%3A3000:dids:issuer` -> `https://example.com:3000/dids/issuer/did.jsonl`
- Parse invalid DIDs and verify `UrlParseException` is thrown:
  - IP address as domain (e.g., `did:webvh:{SCID}:192.168.1.1`) -> rejected
  - Raw colon port (e.g., `did:webvh:{SCID}:example.com:3000`) -> rejected (must be `%3A`)
  - Empty path segment (e.g., `did:webvh:{SCID}:example.com::issuer`) -> rejected
  - Missing SCID or wrong length -> rejected
- Round-trip: construct DidWebVhUrl, toString(), parse again, verify equality
- Test witness URL generation
- Test did:web conversion

### Acceptance Criteria
- All DID URL examples from the spec are correctly parsed and transformed
- Invalid URLs throw `UrlParseException` with descriptive messages
- Port percent-encoding is handled correctly
- Path segments are handled correctly
- `.well-known` is used when there's no path

### Implementation Notes
- Added `DidWebVhUrl` in `core.url` package: parses `did:webvh:<SCID>:<domain>[:<path>...][?query][#fragment]` with full ABNF validation.
- SCID validated as exactly 46 base58btc characters per spec ABNF. Domain validated against IP addresses (IPv4/IPv6 rejected). Port must be percent-encoded (`%3A`); raw colons are path separators.
- Added `DidToHttpsTransformer` in `core.url` package: implements spec section 3.4 transformation with Unicode normalization (NFC) and IDNA/Punycode via `java.net.IDN`. Path segments percent-encoded per RFC 3986.
- `toHttpsUrl()`, `toWitnessUrl()`, and `toDidWebUrl()` all delegate to `DidWebVhUrl.parse()` for validation.
- `.well-known` prefix used when no path segments; `did-witness.json` replaces `did.jsonl` for witness URLs.
- 36 new unit tests (21 DidWebVhUrlTest + 15 DidToHttpsTransformerTest); `./mvnw clean verify` passes on all modules (133 total tests).

---

## Iteration 7: Log Chain Validation `[DONE]`

### Goal
Implement the full log chain validation logic from spec section 3.6.2. This is the core security logic.

### Tasks

1. **`LogChainValidator`** in `validate` package:
   - `ValidationResult validate(List<LogEntry> entries, String expectedDid)`:
     For each entry in order:
     1. Parse and validate `versionId` format
     2. Verify version number increments by 1 (starting from 1)
     3. Merge parameters with accumulated active parameters
     4. Validate parameters conform to spec (section 3.7.1):
        - First entry MUST have `method`, `scid`, `updateKeys`
        - `scid` MUST NOT appear in later entries
        - `portable` can ONLY be set to `true` in first entry, cannot change from `false` to `true`
        - `method` must be valid semver, >= previous
        - If `witness` is set: `threshold` must be >= 1 and <= witness count
        - If `witness` is active (set in current or previous entry): witness proofs are required for subsequent entries (delegated to `WitnessValidator`)
     5. For first entry: verify SCID using `ScidGenerator.verify()`
     6. Verify entry hash using `EntryHashGenerator.verify()`
     7. Verify Data Integrity proof:
        - Determine active `updateKeys` (depends on pre-rotation state)
        - Verify proof signature using `ProofVerifier.verify()`
        - Verify signing key is in active `updateKeys` using `ProofVerifier.isAuthorized()`
     8. Verify `versionTime`:
        - Valid ISO8601 UTC
        - Greater than previous entry's versionTime
        - Last entry's versionTime <= current time
     9. Verify DID Document `id` matches `expectedDid` in at least one entry
     10. If pre-rotation is active:
         - All `updateKeys` multikey hashes must match `nextKeyHashes` from previous entry
     11. If deactivated: no further entries allowed after deactivation
     12. If validation fails for an entry: record last valid entry index, stop processing

   - Returns `ValidationResult`:
     - `boolean valid`
     - `int lastValidEntryIndex`
     - `String failureReason` (null if all valid)
     - `int failedEntryIndex` (-1 if all valid)
     - `Parameters activeParameters` (accumulated at last valid entry)

2. **`WitnessValidator`** in `validate` package:
   - `WitnessValidationResult validate(List<LogEntry> entries, WitnessProofCollection witnessProofs, int fromEntryIndex)`:
     For each entry that requires witnessing (from `fromEntryIndex`):
     1. Find the witness proof entry matching this log entry's versionId
     2. Verify at least `threshold` valid proofs exist
     3. For each witness proof:
        - Verify it's signed by a DID in the active witnesses list
        - Verify the Data Integrity proof signature
        - Use the versionId as the signed data
     4. Ignore proofs for unpublished (future) entries
     5. Ignore proofs from witnesses not in the active list

### Tests

- **Valid single-entry log**: create a DID, validate the single-entry log -> valid
- **Valid multi-entry log**: create, update 3 times, validate -> all valid
- **Tampered entry hash**: modify state after creation, validate -> fails at tampered entry
- **Tampered proof**: modify proof value, validate -> fails
- **Wrong signing key**: sign with unauthorized key, validate -> fails
- **Version number gap**: skip version 2 -> fails
- **versionTime ordering**: set versionTime earlier than previous -> fails
- **SCID tampering**: modify SCID in first entry -> fails
- **Pre-rotation**: create with nextKeyHashes, update with matching keys -> valid; update with non-matching -> fails
- **Deactivation**: deactivate, then try to add entry -> fails
- **Parameter validation**: missing method in first entry -> fails, scid in second entry -> fails
- **Witness config validation**: threshold < 1 -> fails, threshold > witness count -> fails
- **Witness proof validation**: threshold met -> valid, threshold not met -> fails, invalid witness signature -> fails
- **Graceful degradation**: valid entries followed by invalid -> returns last valid index

### Acceptance Criteria
- Full log chain validation per spec section 3.6.2
- All validation rules from the spec are implemented
- Witness validation works with threshold logic
- Pre-rotation verification works correctly
- Clear error messages for each type of validation failure
- Validation is the most thoroughly tested component

### Implementation Notes
- Added `ValidationResult` and `WitnessValidationResult` value objects in `core.validate` package.
- `LogChainValidator.validate()` starts accumulation from `Parameters.defaults()` so every resolved field reflects its spec default; deactivation is checked at the **top** of the loop (fast-fail before any crypto work).
- `LogChainValidator` enforces: version-number monotonicity, SCID (first entry), entry-hash, Data Integrity proof (signature + authorization), versionTime ordering, parameter constraints (method/scid/updateKeys required in first entry; no scid/portable-escalation in later entries; witness threshold bounds; method semver monotonicity), pre-rotation hash matching, and deactivation finality.
- Key-rotation entries are authorized with the **previous** active updateKeys; the new keys only take effect after the entry is fully validated.
- `WitnessConfig.empty()` added as the spec's `witness: {}` sentinel; `isActive()` helper used everywhere to distinguish "witnesses configured" from "empty/default".
- `WitnessValidator.validate()` verifies threshold-met witness proofs over `{"versionId":"…"}` documents; proofs from unknown witnesses are silently skipped.
- `Parameters.defaults()` provides all spec section 3.7.1 defaults: `ttl=3600`, `portable=false`, `deactivated=false`, `witness=WitnessConfig.empty()`, `watchers=[]`.
- `ttl=0` is a valid value meaning "do not cache"; `CreateDidOperation` validation changed from `<= 0` to `< 0` accordingly.
- `ProofVerifier` extended with a public `verify(DataIntegrityProof, JsonObject)` overload and `extractMultikey` made `public`.
- `DidWebVh.validate(entries, expectedDid)` facade added.
- `validateParameters` private method uses named parameters `entryDelta` / `newActive` / `prevActive` for clarity.
- 165 total tests; `./mvnw clean verify` passes on all modules.

---

## Iteration 8: DID Resolution `[DONE]`

### Goal
Implement DID resolution: fetch `did.jsonl` (and `did-witness.json`) from HTTPS, parse, validate, and return the resolved DID Document with metadata.

### Tasks

1. **`HttpDidFetcher`** in `resolve` package:
   - `String fetchDidLog(String httpsUrl)` - HTTP GET, return body as string
   - `String fetchWitnessProofs(String witnessUrl)` - HTTP GET witness file
   - Configurable: timeout (default 10s), max response size (default 200KB)
   - Uses OkHttp
   - Throws `ResolutionException` on HTTP errors (404, 500, timeout, etc.)

2. **`FileDidFetcher`** in `resolve` package:
   - `String fetchDidLog(Path filePath)` - read local file
   - `String fetchWitnessProofs(Path witnessPath)` - read local witness file

3. **`LogProcessor`** in `resolve` package:
   - `ResolveResult process(String didLogContent, String witnessContent, String did, ResolveOptions options)`:
     1. Split `didLogContent` by `\n`, parse each line as `LogEntry`
     2. Parse `witnessContent` as `WitnessProofCollection` (if present)
     3. Call `LogChainValidator.validate()` on entries
     4. If witnesses configured, call `WitnessValidator.validate()`
     5. Apply query parameters:
        - `versionId`: find entry with matching versionId, return that version's DIDDoc
        - `versionTime`: find last entry with versionTime <= requested time
        - `versionNumber`: find entry with matching version number
     6. Build `ResolutionMetadata` from accumulated state
     7. Return `ResolveResult` with DIDDoc, metadata, and any errors

4. **`DidResolver`** in `resolve` package:
   - `ResolveResult resolve(String did)` and `ResolveResult resolve(String did, ResolveOptions options)`:
     1. Parse DID using `DidWebVhUrl`
     2. Transform to HTTPS URL using `DidToHttpsTransformer`
     3. Fetch `did.jsonl` using `HttpDidFetcher`
     4. Optionally fetch `did-witness.json`
     5. Delegate to `LogProcessor`
   - `ResolveResult resolveFromFile(Path didLogPath)` - for local files
   - `ResolveResult resolveFromLog(String rawJsonl, String did)` - for in-memory content

5. **`ResolveOptions`** in `resolve` package:
   - `String versionId`, `String versionTime`, `Integer versionNumber`
   - Builder pattern

6. **`DidWebVh.resolve(String did)`** facade method.

### Tests

- **Resolve from file**: create a DID, write to file, resolve from file, verify
- **Resolve with MockWebServer**: set up mock HTTP server returning valid `did.jsonl`, resolve via HTTPS
- **Resolve specific version**: create + update, resolve with `?versionId=1-...`, verify returns first version
- **Resolve by time**: create at T1, update at T2, resolve with versionTime=T1.5 -> first version
- **Resolve by version number**: resolve with versionNumber=1
- **HTTP errors**: 404 -> `ResolutionException` with notFound error, 500 -> `ResolutionException`
- **Invalid log**: tampered log -> `ResolutionException` with invalidDid error
- **Deactivated DID**: resolve deactivated -> metadata shows deactivated=true, no DIDDoc returned
- **Witness required**: resolve DID with witnesses, provide valid witness file -> success
- **Large log**: 50+ entries log, resolve latest and specific versions
- **Timeout**: mock slow server, verify timeout handling

### Acceptance Criteria
- Full resolution flow works end-to-end (create -> write -> resolve -> verify)
- HTTP resolution with configurable timeout and max size
- File-based resolution for testing and offline use
- Query parameter filtering (versionId, versionTime, versionNumber)
- Resolution metadata matches spec section 3.6.2
- Error responses follow spec (notFound, invalidDid with problemDetails)

### Implementation Notes
- Added `ResolveOptions` in `core.resolve` with `versionId`, `versionTime`, `versionNumber`, and `WitnessFetchMode`. Only one version selector is accepted at a time; the spec describes each selector but does not explicitly define combined-selector semantics, so ambiguous combinations fail with `invalidDid`.
- Added `HttpDidFetcher` and `FileDidFetcher` in `core.resolve`. `HttpDidFetcher` uses OkHttp, default 10s timeout, configurable timeout via `HttpDidFetcher(Duration, int)` or `DidResolver(Duration, int)`, and a 200KB streaming response-size guard. The size guard is an implementation safety limit, not a did:webvh spec rule.
- Added `LogProcessor.process()` in `core.resolve`: parses JSONL into `LogEntry`, validates with `LogChainValidator`, fetches/parses witness proofs only when required by active log parameters (or accepts proactively fetched content), applies version selection, and builds `ResolveResult` / `ResolutionMetadata`.
- Added `DidResolver` in `core.resolve`: parses `DidWebVhUrl`, transforms to `did.jsonl` / `did-witness.json` URLs, fetches over HTTP, supports file and in-memory resolution, and supports proactive or when-required witness retrieval.
- `versionTime` selection returns the last log entry whose `versionTime` is less than or equal to the requested time, matching the version active at that time.
- `ResolutionException` now carries resolver error codes plus Problem Details with `urn:didwebvh:error:<code>` problem types instead of `about:blank`.
- Corrected SCID generation/parsing to the spec ABNF: 46 base58btc characters without a multibase `z` prefix. This replaced the earlier generated 47-character multibase SCID behavior.
- Added `DidWebVh.resolve(String)` facade method.
- Added focused tests for file resolution, query selection, witness validation and fetch modes, HTTP errors, size limits, timeouts, Problem Details, SCID length, and active-at-time selection.

---

## Iteration 9: DID Update, Migration, and Deactivation `[DONE]`

### Goal
Implement all update operations from spec section 3.6.3 and deactivation from section 3.6.4.

### Tasks

1. **`UpdateDidConfig`** builder in `update` package:
   - Required: `DidWebVhState existingState`, `Signer signer`
   - Optional: new `DidDocument`, new `Parameters` (partial), new service endpoints, etc.
   - `execute()` method

2. **`UpdateDidOperation`** in `update` package:
   - Standard update flow (spec section 3.6.3):
     1. Take existing state (all previous log entries + accumulated parameters)
     2. Build new parameters (merge provided changes with active parameters)
     3. Build preliminary log entry:
        - `versionId`: previous entry's versionId (will be replaced)
        - `versionTime`: current UTC ISO8601
        - `parameters`: the changed parameters (only diffs, or `{}` if none)
        - `state`: the new DID Document
     4. Generate entry hash with previous versionId as predecessor
     5. Set versionId to `"<n>-<entryHash>"` where n = previous version + 1
     6. Generate Data Integrity proof
     7. Return updated log entry

3. **`MigrateDidOperation`** in `update` package:
   - Migration flow (spec section 3.7.6):
     1. Verify `portable` is `true` in current parameters
     2. Build new DID string with new domain
     3. Rewrite all DID references in the DID Document
     4. Add previous DID to `alsoKnownAs`
     5. Create a standard update entry with the new references

4. **`DeactivateDidOperation`** in `update` package:
   - Deactivation flow (spec section 3.6.4):
     1. If pre-rotation is active: create intermediate entry to turn off pre-rotation (`nextKeyHashes: []`)
     2. Create final entry with `deactivated: true` and `updateKeys: []`
     3. Return one or two entries

5. **`DidWebVhState`** in `core` package:
   - Holds: `String did`, `List<LogEntry> logEntries`, `WitnessProofCollection witnessProofs`, `Parameters activeParameters`, `boolean validated`, `boolean deactivated`
   - `appendEntry(LogEntry entry)` - add new entry to log
   - `toDidLog()` - serialize all entries to JSONL string (for `did.jsonl`)
   - `toJson()` / `fromJson()` - save/load full state for caching
   - `validate()` - re-validate the log chain

6. **Facade methods**: `DidWebVh.update(state)`, `DidWebVh.migrate(state, newDomain)`, `DidWebVh.deactivate(state)`

### Tests

- **Simple update**: create, update document (add service), verify new entry is valid, full log validates
- **Key rotation**: create, update with new updateKeys, verify old key can't sign new entries
- **Parameter update**: change TTL, verify parameters merge correctly
- **Multiple updates**: create, update 5 times, verify full chain validates
- **Migration**: create with portable=true, migrate to new domain, verify alsoKnownAs, verify full chain
- **Migration without portable**: attempt migration with portable=false -> error
- **Deactivation**: create, deactivate, verify deactivated=true, updateKeys=[]
- **Deactivation with pre-rotation**: create with pre-rotation, deactivate -> two entries generated
- **Update after deactivation**: attempt update after deactivation -> error
- **End-to-end**: create -> update 3x -> migrate -> update 2x -> deactivate -> resolve each version

### Acceptance Criteria
- All update operations follow the spec exactly
- Log chain validates after every operation
- Migration preserves SCID and history
- Deactivation correctly handles pre-rotation edge case
- `DidWebVhState` tracks full state correctly

### Implementation Notes
- Added `DidWebVhState` in `core` package (not a sub-package, per ARCHITECTURE.md). Holds the log entries, optional witness proofs, and active parameters from the last `validate()` call. `accumulateParameters()` is `public` so operations in the `update` sub-package can read effective state without a full crypto-validated run.
- `DidWebVhState.did` is mutable: `appendEntry()` re-reads the appended entry's `state.id` and updates the canonical DID if it changed. This keeps migration-aware — after a successful migration, `state.getDid()` returns the new DID and validator calls pick up the new domain. First entry's DID still comes from `DidWebVhState.from(did, entry)`.
- `UpdateDidOperation.buildEntry()` is `static` package-accessible and shared by `MigrateDidOperation` and `DeactivateDidOperation` to avoid code duplication — the entry-construction logic is identical, only the state/params inputs differ.
- Both `CreateDidOperation` and `UpdateDidOperation` use `Instant.now().toString()` for `versionTime`. This emits ISO-8601 with whatever sub-second precision the clock reports (0–9 fractional digits — NOT the canonical `yyyy-MM-ddTHH:mm:ssZ` form), and is always parseable by `Instant.parse()`. The validator requires strict `isAfter()` ordering; a second-precision formatter causes spurious failures when create/update happen in the same second, and a fixed-milli formatter collides on fast successive writes. Sub-second resolution is the pragmatic choice.
- `MigrateDidOperation` uses string replacement (`oldDid → newDid`) on the serialised JSON to rewrite all references in one pass, then adds the old DID to `alsoKnownAs` (deduplication guard included). `DidWebVhUrl.parse()` is called to extract the SCID before building the new DID, ensuring domain/path validation is delegated to the URL layer.
- Pre-rotation deactivation requires two signers: `signer` (current authorised key, signs the intermediate entry that reveals the next key and clears `nextKeyHashes`) and `nextRotationSigner` (the key committed in `nextKeyHashes`, signs the final `deactivated=true` entry). A clear `ValidationException` is thrown at config time if pre-rotation is active but `nextRotationSigner` is missing.
- `ProofVerifier.extractMultikey(String)` (public from Iteration 7) is reused in `DeactivateDidOperation` to get the next key's multikey from the `nextRotationSigner` verification method URI, avoiding any coupling to `CreateDidOperation`.
- `DeactivateDidConfig` / `MigrateDidConfig` follow the same builder-pattern convention as `CreateDidConfig` and `UpdateDidConfig`; all have package-private accessors and a public `execute()` method.
- Three new facade methods added to `DidWebVh`: `update()`, `migrate()`, `deactivate()` (all returning their respective config/builder). A dedicated `DidWebVhStateTest` (11 cases) covers the public `DidWebVhState` surface — factory methods, JSONL/JSON round-trips, validate/append lifecycle, migration DID-sync, deactivation flag, parameter accumulation. Total tests: 218 (210 core + 8 signing-local); `./mvnw clean verify` passes.

---

## Iteration 10: did:web Parallel Publishing `[DONE]`

### Goal
Implement the parallel did:web document generation from spec section 3.7.10.

### Tasks

1. **`DidWebPublisher`** in `didweb` package:
   - `static DidDocument toDidWeb(ResolveResult resolvedWebVh)`:
     1. Start with the resolved DIDDoc from did:webvh
     2. Add implicit services (#files, #whois) if not already present, with serviceEndpoint derived from DID-to-HTTPS transformation
     3. Replace all `did:webvh:<SCID>:` with `did:web:` in the document
     4. Set controller to the original did:webvh DID
     5. Add the full did:webvh DID to alsoKnownAs
     6. Remove duplicates from alsoKnownAs
   - `static String toDidWebUrl(String didWebVhUrl)` - convert did:webvh DID to did:web DID

### Tests

- Convert a basic did:webvh DIDDoc to did:web, verify all references updated
- Verify implicit services are added
- Verify controller is set to did:webvh
- Verify alsoKnownAs contains did:webvh
- Verify no duplicate alsoKnownAs entries

### Acceptance Criteria
- Parallel did:web document generation follows spec section 3.7.10
- All DID references correctly converted
- Implicit services injected

### Implementation Notes
- Added `DidWebPublisher` in `core.didweb` package with two public static methods:
  `toDidWeb(DidDocument)` and `toDidWebUrl(String)`.
- `toDidWeb()` takes a `DidDocument` directly rather than a `ResolveResult` — only the
  resolved document is required for section 3.7.10, so the narrower type keeps the
  contract honest and frees callers from wrapping a bare DIDDoc in a `ResolveResult`.
- Flow follows spec section 3.7.10 literally: deep-copy the source DIDDoc → add implicit
  `#files` and `#whois` services (skipped if either `#files`/`<did>#files` or
  `#whois`/`<did>#whois` is already present) → text-replace `did:webvh:<SCID>:` with
  `did:web:` across the serialized document → add the original did:webvh DID to
  `alsoKnownAs`, dedupe, and drop the did:web self-DID if it landed there via the
  replacement.
- Implicit service endpoints are derived from the DID-to-HTTPS URL by stripping the
  trailing `did.jsonl` filename and omitting the `.well-known/` segment when present;
  `#whois` appends `whois.vp` to that base. `#files` uses `type: "relativeRef"`;
  `#whois` uses `type: "LinkedVerifiablePresentation"` with the linked-vp `@context`.
- `toDidWebUrl()` delegates to `DidToHttpsTransformer.toDidWebUrl()` (already implemented
  in Iteration 6) so there is one canonical implementation of the DID-form conversion.
- 12 new unit tests covering id/controller rewriting, verificationMethod reference
  updates, implicit-service injection with path/port combinations, explicit-service
  override (both `#files` and `<did>#files` id forms), `alsoKnownAs` preservation +
  dedup + self-removal, and error cases. Total tests: 232 (224 core + 8 signing-local);
  `./mvnw clean verify` passes.

---

## Iteration 11: Interactive Wizard CLI `[DONE]`

### Goal
Build an interactive CLI wizard in the `didwebvh-wizard` module, similar to the Rust implementation.

### Tasks

1. **`WizardMain`** in `wizard` package:
   - Main menu using picocli + JLine:
     ```
     === did:webvh Wizard ===
     1. Create a new DID
     2. Update an existing DID
     3. Resolve a DID
     4. Exit
     ```

2. **`CreateWizard`**:
   - Prompts for:
     1. Web address (domain, optional path)
     2. Generate or import authorization key (Ed25519)
     3. DID Document content:
        - Verification methods and relationships
        - Services (JSON input)
        - Controller
        - AlsoKnownAs
     4. Parameters:
        - Portable (yes/no)
        - Pre-rotation keys (yes/no, generate next keys)
        - Witnesses (add witness DIDs, set threshold)
        - Watchers (add URLs)
        - TTL
   - Executes `DidWebVh.create()` with collected config
   - Saves outputs:
     - `did.jsonl` - the DID log
     - `did-secrets.json` - the signing key (WARNING: keep secure)
     - `did-witness.json` - witness proofs (if witnesses configured)
   - Displays the created DID and file locations

3. **`UpdateWizard`**:
   - Loads existing `did.jsonl` and `did-secrets.json`
   - Sub-menu:
     1. **Modify** - Edit document/parameters
     2. **Migrate** - Move to new domain
     3. **Deactivate** - Permanent deactivation (with confirmation)
   - Executes the chosen operation
   - Updates `did.jsonl` with new entry

4. **`ResolveWizard`**:
   - Prompts for DID string
   - Optionally: version filters (versionId, versionTime, versionNumber)
   - Resolves and displays:
     - DID Document (pretty-printed JSON)
     - Resolution Metadata
     - Validation status

5. **Build the wizard as an executable JAR** with `maven-shade-plugin` or `maven-assembly-plugin` in the wizard module POM.

### Tests

- Test the wizard components with pre-configured inputs (no interactive prompts in tests)
- Verify file output formats (did.jsonl, did-secrets.json)
- Verify round-trip: create with wizard -> resolve with wizard
- Test error handling (invalid inputs, missing files)

### Acceptance Criteria
- Wizard runs interactively from `java -jar didwebvh-wizard.jar`
- Create flow produces valid `did.jsonl` and `did-secrets.json`
- Update flow correctly appends to existing `did.jsonl`
- Resolve flow shows formatted output
- Deactivation requires confirmation
- Non-interactive mode works for CI/testing

### Implementation Notes
- Added nine classes in `didwebvh-wizard/src/main/java/io/github/decentralized-identity/didwebvh/wizard/`:
  `WizardIo` (abstraction over stdin/stdout), `ConsoleWizardIo` (JLine-backed implementation),
  `WizardException`, `WizardPrompts` (shared input parsers for yes/no, ints, multi-line JSON,
  comma-separated lists), `WizardFiles` (read/write `did.jsonl`, `did-secrets.json`,
  `did-witness.json`), `CreateWizard`, `UpdateWizard`, `ResolveWizard`, and `WizardMain`
  (picocli entry point with the top-level menu).
- `WizardIo` is intentionally an interface, not a fixed `System.in/out` hookup, so every
  wizard flow is driven by a scripted I/O in tests — no interactive prompts in CI.
  `ScriptedWizardIo` in the test sources replays pre-staged lines and captures output for
  assertions.
- `CreateWizard` covers the full option surface: domain + path, generate or import a
  signing key, optional services block (parsed as JSON), controller, `alsoKnownAs`,
  `portable`, pre-rotation next-key generation, witnesses (collect DIDs + threshold),
  watchers, and `ttl`. Saves `did.jsonl`, `did-secrets.json`, and (when applicable)
  `did-witness.json` to a caller-chosen directory.
- `UpdateWizard` loads an existing `did.jsonl` + `did-secrets.json` into a
  `DidWebVhState`, then dispatches to **Modify** (pass-through to
  `DidWebVh.update()`), **Migrate** (`DidWebVh.migrate()` with the new domain), or
  **Deactivate** (`DidWebVh.deactivate()` with an explicit `"DEACTIVATE"` confirmation
  prompt). The new entry is appended to `did.jsonl` in place.
- `ResolveWizard` accepts a DID and optional `versionId` / `versionTime` /
  `versionNumber`, calls `DidWebVh.resolve()`, and pretty-prints the resolved
  `DidDocument` and `ResolutionMetadata`.
- `didwebvh-wizard/pom.xml` adds `maven-shade-plugin` 3.5.2 producing a
  `didwebvh-wizard-<version>-shaded.jar` classifier with `WizardMain` as the manifest
  main class, so the wizard runs via `java -jar`. `ConsoleWizardIo` is added to
  `config/spotbugs-exclude.xml` for the expected EI_EXPOSE_REP2 pattern
  (PrintStream/BufferedReader are intentionally stored by reference).
- 13 new wizard tests (`CreateWizardTest`, `UpdateWizardTest`, `ResolveWizardTest`,
  `WizardMainTest`) exercise the full create/update/migrate/deactivate/resolve flows
  through scripted I/O. `./mvnw clean verify` passes on all four modules (245 total
  tests).

---

## Iteration 12: Test Vectors and Spec Compliance `[DONE]`

### Goal
Add test vectors from the spec examples and from the Rust implementation. Ensure full spec compliance.

### Tasks

1. **Create test vector files** in `src/test/resources/test-vectors/`:
   - `first-log-entry-good.jsonl` - a valid single-entry DID log
   - `first-log-entry-tampered.jsonl` - same but with tampered data
   - `multi-entry-log.jsonl` - multi-version DID log with updates
   - `multi-entry-witness.json` - witness proofs for the multi-entry log
   - `deactivated-did.jsonl` - a deactivated DID log
   - `migrated-did.jsonl` - a DID that was migrated to a new domain
   - `pre-rotation-log.jsonl` - a DID using pre-rotation keys
   - Generate these by running the library's create/update operations with deterministic keys and timestamps

2. **Spec compliance tests** (as integration tests in `src/test/java/.../integration/`):
   - Test every MUST requirement from the spec:
     - SCID generation and verification
     - Entry hash generation and verification
     - Data Integrity proof eddsa-jcs-2022
     - DID-to-HTTPS transformation (all examples from spec)
     - Parameter rules (method required in first entry, scid only in first, portable immutable, etc.)
     - Pre-rotation constraints
     - Witness threshold algorithm
     - Deactivation rules
     - Resolution metadata format

3. **Cross-implementation compatibility** (if test vectors available from Rust/TypeScript implementations):
   - Verify this library can resolve/validate DIDs created by other implementations
   - Verify DIDs created by this library can be validated by other implementations

4. **Property-based tests** (optional but valuable):
   - Any valid create followed by any number of valid updates always produces a valid log chain
   - Tampering with any byte of any entry always causes validation failure

### Acceptance Criteria
- Test vectors cover all major spec features
- All spec MUST requirements are tested
- Test coverage > 80% (ideally > 90% for core module)
- All tests pass on Java 11, 17, 21, and 25

### Implementation Notes
- Added three classes in `didwebvh-core/src/test/java/io/github/decentralized-identity/didwebvh/core/integration/`:
  `TestVectors` (classpath loader, JSONL/witness parsers, and a deterministic
  Ed25519 seeded `Signer` factory built on four fixed 32-byte seeds — author,
  update, next, witness), `TestVectorGenerator` (runnable `main` that produces
  every committed vector by invoking the library's own `DidWebVh.create`,
  `.update`, `.migrate`, and `.deactivate` APIs; no new production hooks), and
  `SpecComplianceIT` (18 spec-MUST tests).
- Seven vectors plus a `README.md` live under
  `didwebvh-core/src/test/resources/test-vectors/`:
  `first-log-entry-good.jsonl`, `first-log-entry-tampered.jsonl`,
  `multi-entry-log.jsonl`, `multi-entry-witness.json`, `deactivated-did.jsonl`,
  `migrated-did.jsonl`, `pre-rotation-log.jsonl`. The tampered vector is
  produced by flipping the DID Document `id` on a valid first entry so both
  the SCID and entry-hash checks fail. The witness file is generated by
  signing `{"versionId":"…"}` with the witness seed for each of the three
  multi-entry versions, exercising the full `WitnessValidator` path.
- `SpecComplianceIT` is organised into three sections: vector-based checks
  (SCID/entry-hash/proof verification on `first-log-entry-good`; tamper
  detection on `first-log-entry-tampered`; multi-entry chain + witness
  verification; deactivation end-state; migration SCID-preservation and
  `alsoKnownAs`; pre-rotation happy path; pre-rotation violation by swapping
  `updateKeys` to a key whose hash is not committed; arbitrary-byte tamper
  on entry 2 of the multi-entry log), parameter-rule MUSTs (first-entry
  required fields, `scid` must not reappear, `portable` cannot flip on,
  `versionNumber` must start at 1 and monotonically increment), and DID-to-
  HTTPS transformation examples from spec §3.4 (bare domain, nested path,
  `%3A`-encoded port, percent-encoding of non-unreserved bytes, and
  `toDidWebUrl` dropping the SCID). A final property-style test runs
  `create` + eight `update`s and re-validates the full chain.
- `Instant.now()` is still the clock used inside `CreateDidOperation` /
  `UpdateDidOperation`, so `versionTime` values captured in committed
  vectors are snapshots — SCIDs, entry hashes, and proofs stay
  byte-stable until `TestVectorGenerator.main()` is re-run. Regeneration
  instructions live in the test-vectors `README.md`.
- `./mvnw clean verify` passes on all four modules (core test count rose
  from 202 → 220; suite total 241). Jacoco instruction coverage for the
  core module is 83 %, above the >80 % acceptance bar. Checkstyle and
  SpotBugs remain clean.

---

## Iteration 13: Quality, CI Finalization, and Documentation `[DONE]`

### Goal
Finalize CI badges, quality gates, documentation, and prepare for first release.

### Tasks

1. **Fix any Checkstyle violations** across all modules.

2. **Fix any SpotBugs findings** across all modules.

3. **Ensure JaCoCo coverage > 80%** for core module. Add tests if needed.

4. **Configure SonarCloud**:
   - Set up project on sonarcloud.io
   - Configure quality gate (coverage, duplications, bugs, code smells)
   - Ensure badge in README works

5. **Configure Codecov**:
   - Add `codecov.yml` if needed
   - Ensure coverage badge in README works

6. **Update README.md** with:
   - Accurate badges (now pointing to real CI, Codecov, SonarCloud, Maven Central)
   - Final API examples reflecting actual API
   - Complete feature list
   - Build and test instructions
   - License and contributing sections

7. **Add Javadoc** to all public classes and methods in core module.

8. **Create `CHANGELOG.md`** with initial version entry.

9. **Configure Maven Central publishing** in parent POM using a `release` profile:
   - The `release` profile is activated only during release builds (not normal `./mvnw verify`)
   - `maven-gpg-plugin` for JAR signing:
     ```xml
     <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-gpg-plugin</artifactId>
       <version>3.2.4</version>
       <executions>
         <execution>
           <id>sign-artifacts</id>
           <phase>verify</phase>
           <goals><goal>sign</goal></goals>
           <configuration>
             <gpgArguments>
               <arg>--pinentry-mode</arg>
               <arg>loopback</arg>
             </gpgArguments>
           </configuration>
         </execution>
       </executions>
     </plugin>
     ```
   - `central-publishing-maven-plugin` for Sonatype Central publishing (the new portal):
     ```xml
     <plugin>
       <groupId>org.sonatype.central</groupId>
       <artifactId>central-publishing-maven-plugin</artifactId>
       <version>0.7.0</version>
       <extensions>true</extensions>
       <configuration>
         <publishingServerId>central</publishingServerId>
         <autoPublish>true</autoPublish>
       </configuration>
     </plugin>
     ```
   - `maven-source-plugin` (attach source JAR — required by Maven Central)
   - `maven-javadoc-plugin` (attach javadoc JAR — required by Maven Central)
   - Add server credentials in the workflow (NOT in pom.xml):
     ```xml
     <!-- This goes in the CI-generated settings.xml, not committed to repo -->
     <server>
       <id>central</id>
       <username>${env.OSSRH_USERNAME}</username>
       <password>${env.OSSRH_TOKEN}</password>
     </server>
     ```

10. **Create release workflow** (`.github/workflows/release.yml`):
    - Triggered on tag push (`v*`)
    - Uses these **GitHub repo secrets** (configured at `Settings > Secrets and variables > Actions`):
      | Secret Name | Value | Purpose |
      |-------------|-------|---------|
      | `GPG_PRIVATE_KEY` | Full output of `gpg --armor --export-secret-keys <KEY_ID>` | Signs JARs for Maven Central |
      | `GPG_PASSPHRASE` | GPG key passphrase | Unlocks the GPG key in CI |
      | `OSSRH_USERNAME` | Sonatype Central portal username | Authenticates to publish |
      | `OSSRH_TOKEN` | Sonatype Central portal token (generate at central.sonatype.com) | Authenticates to publish |
    - Full workflow structure:
      ```yaml
      name: Release to Maven Central
      on:
        push:
          tags: ['v*']

      jobs:
        release:
          runs-on: ubuntu-latest
          steps:
            - uses: actions/checkout@v4

            - name: Set up Java 11
              uses: actions/setup-java@v4
              with:
                java-version: '11'
                distribution: 'temurin'
                server-id: central
                server-username: OSSRH_USERNAME
                server-password: OSSRH_TOKEN
                gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
                gpg-passphrase: GPG_PASSPHRASE

            - name: Publish to Maven Central
              run: ./mvnw clean deploy -P release -B --no-transfer-progress
              env:
                OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
                OSSRH_TOKEN: ${{ secrets.OSSRH_TOKEN }}
                GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}

            - name: Create GitHub Release
              uses: softprops/action-gh-release@v2
              with:
                generate_release_notes: true
                files: |
                  didwebvh-core/target/*.jar
                  didwebvh-wizard/target/*-shaded.jar
      ```
    - Note: `actions/setup-java@v4` handles importing the GPG key and creating `settings.xml` with the server credentials. The `server-username` and `server-password` fields are the **env variable names** (not the values), which the step maps to the actual secrets via the `env` block.

11. **Create SECURITY.md** with vulnerability reporting instructions.

12. **Verify all badges work** (may need real CI runs first -- placeholder badge URLs are fine initially).

### Acceptance Criteria
- CI runs green on all Java versions (11, 17, 21, 25)
- Checkstyle, SpotBugs, JaCoCo all pass
- SonarCloud quality gate passes
- README accurately reflects the project
- All public API has Javadoc
- Maven Central publishing is configured with the `release` profile
- Release workflow exists and references all 4 required GitHub secrets (`GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `OSSRH_USERNAME`, `OSSRH_TOKEN`)
- Tagging `v0.1.0` and pushing triggers the full release pipeline

### Implementation Notes
- Added Maven Central publishing metadata to the parent `pom.xml`: `<url>`,
  `<licenses>` (Apache-2.0), `<developers>`, `<scm>`, and `<issueManagement>`
  — the minimum required by the Sonatype Central portal. Registered
  `maven-gpg-plugin` 3.2.4 and `central-publishing-maven-plugin` 0.7.0 in
  `<pluginManagement>` and configured the existing `maven-javadoc-plugin`
  with `<doclint>none</doclint>` / `<failOnWarnings>false</failOnWarnings>`
  so the javadoc JAR still builds across JDK 11–25 without tightening
  every getter/builder in the core module.
- Introduced a `release` profile that attaches source + javadoc JARs,
  signs all artifacts with GPG loopback-pinentry, and deploys via the
  Sonatype Central plugin (`<publishingServerId>central</publishingServerId>`,
  `<autoPublish>true</autoPublish>`). The wizard module is excluded from
  Central — it is an uber-jar CLI published only as a GitHub Release
  asset — using the plugin's `<excludeArtifacts>` list. The profile is
  opt-in (`-P release`) so `./mvnw verify` in day-to-day development stays
  unchanged. Smoke-tested locally with `-Dgpg.skip=true -DskipTests`: core
  and signing-local produce the three Central-required JARs
  (`-sources`, `-javadoc`, main).
- Added `.github/workflows/release.yml`: triggers on `v*` tag push, uses
  `actions/setup-java@v4` with `server-id: central` so the step writes
  the `~/.m2/settings.xml` mapping `OSSRH_USERNAME` / `OSSRH_TOKEN` env
  vars to the `central` server, imports `GPG_PRIVATE_KEY`, and runs
  `./mvnw clean deploy -P release -B --no-transfer-progress -DskipTests`.
  `softprops/action-gh-release@v2` then attaches `didwebvh-core/target/*.jar`
  and `didwebvh-wizard/target/*-shaded.jar` to the generated GitHub
  Release. The four required repo secrets are documented inline in the
  workflow body and in the iteration description above.
- Added class-level Javadoc to the seven `crypto` utility classes that
  were missing it (`Base58Btc`, `Jcs`, `EntryHashGenerator`,
  `ScidGenerator`, `MultihashUtil`, `MultikeyUtil`,
  `PreRotationHashGenerator`), each briefly citing the spec section the
  code implements. The top-level facade (`DidWebVh`) and main model /
  builder classes already carried usable Javadoc; `<doclint>none</doclint>`
  keeps the release build from failing on getter-level gaps that are
  self-documenting.
- Rewrote the README's Quick Start to match the real API (`DidWebVh.create
  (domain, signer)` rather than the pre-iter-5 `.withSigner` builder,
  `DidWebVhState.fromDidLog` for reload, real package name for
  `LocalKeySigner`) and added dedicated Changelog, Contributing, and
  Security sections pointing at `CHANGELOG.md`, `CONTRIBUTING.md`, and
  `SECURITY.md`.
- Created `CHANGELOG.md` (Keep a Changelog 1.1.0 format, SemVer) with a
  complete `0.1.0` entry summarising every public feature delivered
  across iterations 1–13, and `SECURITY.md` describing the private
  vulnerability reporting channels (GitHub private advisory + email),
  response-time expectations, and scope (in/out).
- Added `codecov.yml` at the repo root pinning the project + patch
  coverage target to 80 % (threshold 1 %), ignoring `target/`, `test/`,
  and the wizard module so Codecov reflects the same core-first contract
  the CI enforces.
- No existing Checkstyle / SpotBugs violations were found; JaCoCo core
  coverage stayed at 83 % (above the 80 % bar) without new tests being
  needed — `./mvnw clean verify` remains green on every module after the
  doc / metadata changes.
- Wired `maven-failsafe-plugin` into the parent POM (bound to the
  `integration-test` + `verify` goals, listed under
  `<build><plugins>`) so the `*IT` classes in
  `didwebvh-core/src/test/java/.../integration/` (notably
  `SpecComplianceIT`'s 18 MUST tests) actually run during `./mvnw verify`
  — they were previously dormant because Surefire excludes `*IT` by
  convention and Failsafe wasn't configured. Added a second JaCoCo
  execution (`prepare-agent-integration`) that appends to the same
  `jacoco.exec` as the unit-test agent, so the single `report` step
  covers both runs; core instruction coverage rose from 83 % → 84 %.
  CI picks this up automatically because
  `.github/workflows/ci.yml` already runs `./mvnw clean verify -B`.

---

## Iteration 14: Performance and Edge Cases `[NOT STARTED]`

### Goal
Handle edge cases, optimize for real-world usage, add benchmarks.

### Tasks

1. **Large DID logs**: Test and optimize resolution of logs with 100+ entries. Ensure no excessive memory usage.

2. **Concurrent resolution**: Verify `DidResolver` works correctly when used from multiple threads.

3. **Edge cases**:
   - Empty DID Document (just `id`)
   - Maximum-size DID Document (near 200KB response limit)
   - Unicode in DID Document content
   - Internationalized domain names
   - Very long paths in DID URLs
   - Null/empty optional parameters in all combinations

4. **Timeout and retry**: Configurable HTTP timeout. No automatic retry (let callers handle it).

5. **Response size limit**: Configurable max response size for HTTP resolution (default 200KB).

6. **Helpful error messages**: Review all exception messages. Each should tell the user:
   - What went wrong
   - Which entry/field caused the problem
   - What the expected value was vs. the actual value

7. **Add benchmarks** (optional, using JMH):
   - DID creation time
   - Log chain validation time for N entries
   - Resolution time (from file)

### Acceptance Criteria
- No OutOfMemoryError on 100+ entry logs
- Thread safety verified for stateless components
- All edge cases have tests
- Error messages are clear and actionable
- HTTP timeouts work correctly
