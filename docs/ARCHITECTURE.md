# Architecture

This document describes the technical architecture of `didwebvh-java`, a Java 11+ implementation of the [did:webvh v1.0 specification](https://identity.foundation/didwebvh/v1.0/).

## High-Level Design

```
                    +--------------------------+
                    |       DidWebVh           |  <-- Public facade
                    |  create / resolve /      |
                    |  update / deactivate     |
                    +-----------+--------------+
                                |
          +---------------------+---------------------+
          |                     |                     |
  +-------v------+    +--------v-------+    +--------v-------+
  |    Create     |    |    Resolve     |    |    Update      |
  | CreateConfig  |    | ResolveOptions |    | UpdateConfig   |
  | (builder)     |    | HttpResolver   |    | (builder)      |
  +-------+------+    | FileResolver   |    +--------+-------+
          |            +--------+-------+             |
          |                     |                     |
          +----------+----------+----------+----------+
                     |                     |
              +------v------+      +-------v-------+
              |   Validate  |      |    Model       |
              | LogChain    |      | LogEntry       |
              | EntryHash   |      | Parameters     |
              | ScidVerify  |      | DidDocument    |
              | WitnessVfy  |      | DataIntegrity  |
              +------+------+      | Proof          |
                     |             +-------+--------+
              +------v------+              |
              |   Crypto    |      +-------v-------+
              | Jcs         |      |   Signing     |
              | Multihash   |      | Signer (iface)|
              | Base58      |      | ProofGenerator|
              | ScidGen     |      +---------------+
              | EntryHashGen|
              +-------------+
```

## Module Layout

### `didwebvh-core` (main library)

The core module contains everything needed to create, resolve, update, and validate did:webvh DIDs. It has zero dependencies on any specific key management system beyond what's needed for verification.

#### Package: `model`

Pure data classes representing the spec's data structures. Immutable where possible.

| Class | Spec Concept | Description |
|-------|-------------|-------------|
| `LogEntry` | DID Log Entry | One entry in the `did.jsonl` file: versionId, versionTime, parameters, state, proof |
| `Parameters` | DID Method Parameters | method, scid, updateKeys, nextKeyHashes, witness, watchers, portable, deactivated, ttl |
| `DidDocument` | DIDDoc / state | The DID Document (state field of a log entry). Thin wrapper around a JSON object. |
| `DataIntegrityProof` | Data Integrity proof | type, cryptosuite, verificationMethod, proofPurpose, created, proofValue |
| `WitnessConfig` | witness parameter | threshold + list of witness entries (id as did:key) |
| `WitnessProofCollection` | did-witness.json | Array of {versionId, proof[]} entries |
| `VersionId` | versionId | Parsed version ID: version number + entry hash |
| `DidWebVhUrl` | DID URL | Parsed did:webvh URL with SCID, domain, port, path |
| `ResolveResult` | Resolution result | Resolved DID document + DID Resolution Metadata |
| `ResolutionMetadata` | DID Resolution Metadata | versionId, versionTime, created, updated, scid, portable, deactivated, ttl, witness, watchers |

All model classes use Gson annotations for JSON serialization. `LogEntry` supports both full-object and JSONL (single-line) serialization.

#### Package: `crypto`

Cryptographic operations required by the spec. No signing here -- only hashing, canonicalization, and encoding.

| Class | Purpose |
|-------|---------|
| `Jcs` | JSON Canonicalization Scheme (RFC 8785). Takes a JSON string or object, returns canonicalized bytes. |
| `MultihashUtil` | Wraps multihash library. `encode(algorithm, data)` returns multihash bytes. |
| `Base58Btc` | Base58btc encode/decode. |
| `ScidGenerator` | Generates SCID: `base58btc(multihash(JCS(preliminary_entry), SHA-256))` |
| `EntryHashGenerator` | Generates entry hash: `base58btc(multihash(JCS(entry_without_proof), SHA-256))` |
| `MultikeyUtil` | Encode/decode multikey format for public keys. Extract key type and raw bytes from multikey string. |
| `PreRotationHashGenerator` | Generates pre-rotation key hashes: `base58btc(multihash(multikey))` |

#### Package: `signing`

The signing abstraction and proof generation.

| Class | Purpose |
|-------|---------|
| `Signer` (interface) | `keyType()`, `verificationMethod()`, `sign(byte[])`. This is the adapter interface. |
| `SigningException` | Unchecked exception for signing failures. |
| `ProofGenerator` | Takes a `Signer` and a log entry (without proof), produces a `DataIntegrityProof`. Flow: strip proof -> JCS canonicalize -> sign -> construct proof object. |
| `ProofVerifier` | Verifies a `DataIntegrityProof` against a log entry using the public key from `verificationMethod`. Extracts the public key from the did:key URI, verifies the Ed25519 signature over the JCS-canonicalized entry. |

#### Package: `create`

| Class | Purpose |
|-------|---------|
| `CreateDidConfig` | Builder for DID creation parameters: domain, path, signer, document content, parameters (portable, witnesses, watchers, ttl, pre-rotation keys). |
| `CreateDidOperation` | Executes the creation: builds preliminary entry with `{SCID}` placeholders, generates SCID, replaces placeholders, generates entry hash, generates versionId, generates proof, returns `CreateDidResult`. |
| `CreateDidResult` | The created DID string, the first `LogEntry`, and optionally witness proofs. |

#### Package: `update`

| Class | Purpose |
|-------|---------|
| `UpdateDidConfig` | Builder for DID update parameters: the existing log state, new document, new parameters, signer. |
| `UpdateDidOperation` | Executes the update: builds preliminary entry with previous versionId, generates entry hash, generates new versionId, generates proof. |
| `MigrateDidOperation` | Handles domain migration: validates portability, rewrites DID references, adds alsoKnownAs for prior DID. |
| `DeactivateDidOperation` | Handles deactivation: sets `deactivated: true`, clears `updateKeys`. If pre-rotation is active, generates intermediate entry to disable it first. |
| `UpdateDidResult` | The updated `LogEntry` (or entries, for deactivation with pre-rotation). |

#### Package: `resolve`

| Class | Purpose |
|-------|---------|
| `DidResolver` | Main resolver. Takes a DID string, resolves to `ResolveResult`. Uses `DidToHttpsTransformer` to get the URL, fetches `did.jsonl` (and optionally `did-witness.json`), delegates to `LogProcessor`. |
| `HttpDidFetcher` | Fetches `did.jsonl` and `did-witness.json` from HTTPS URLs via OkHttp. Configurable timeout and max response size. |
| `FileDidFetcher` | Loads `did.jsonl` from local file (for testing and offline use). |
| `LogProcessor` | Parses JSONL into `LogEntry` list, delegates to `LogChainValidator`, applies query parameters (?versionId, ?versionTime, ?versionNumber), returns `ResolveResult`. |
| `DidToHttpsTransformer` | Implements the DID-to-HTTPS transformation algorithm from the spec. Handles domain, port, path, `.well-known`, and internationalized domain names. |
| `ResolveOptions` | Options for resolution: versionId, versionTime, versionNumber filters. |

#### Package: `validate`

| Class | Purpose |
|-------|---------|
| `LogChainValidator` | Walks the log entry chain. For each entry: validates parameters, verifies entry hash, verifies SCID (first entry), verifies Data Integrity proof against authorized keys, checks versionTime ordering, checks pre-rotation constraints. Returns validation result with last valid entry index. |
| `WitnessValidator` | Validates witness proofs from `did-witness.json`. For each witnessed entry: checks that `threshold` valid witness proofs exist, verifies each witness proof signature. |

#### Package: `url`

| Class | Purpose |
|-------|---------|
| `DidWebVhUrl` | Parses `did:webvh:<SCID>:<domain>[:path...]` into components. Validates ABNF. |
| `DidToHttpsTransformer` | (see resolve package above; may live here instead if shared) |

#### Package: `witness`

| Class | Purpose |
|-------|---------|
| `WitnessConfig` | Data class for the `witness` parameter: threshold, list of witnesses. |
| `WitnessProofCollection` | Data class for `did-witness.json`: list of {versionId, proof[]}. |

#### Package: `didweb`

| Class | Purpose |
|-------|---------|
| `DidWebPublisher` | Converts a resolved did:webvh DIDDoc into a parallel did:web DIDDoc. Strips SCID from references, adds implicit services, manages alsoKnownAs. |

#### Package: `core`

| Class | Purpose |
|-------|---------|
| `DidWebVh` | The public facade. Static methods `create()`, `resolve()`, `update()`, `deactivate()` that return builders. This is the main entry point for users. |
| `DidWebVhState` | Holds the full state of a DID: all log entries, witness proofs, validation status. Supports `save()`/`load()` for caching. |
| `DidWebVhException` | Base unchecked exception for all library errors. Subclasses: `ValidationException`, `ResolutionException`, `SigningException`. |

### `didwebvh-signing-local`

A single-class module providing `LocalKeySigner` that loads Ed25519 keys from JSON files (compatible with the Rust implementation's `did-secrets.json` format).

```java
public class LocalKeySigner implements Signer {
    public static LocalKeySigner generate();           // Generate new Ed25519 keypair
    public static LocalKeySigner fromJson(String json); // Load from JSON
    public String toJson();                             // Export to JSON

    @Override public String keyType();
    @Override public String verificationMethod();
    @Override public byte[] sign(byte[] data);
}
```

### `didwebvh-wizard`

Interactive CLI using picocli + JLine. Menu-driven wizard similar to the Rust implementation:

```
=== did:webvh Wizard ===
1. Create a new DID
2. Update an existing DID
3. Resolve a DID
4. Exit
```

The wizard uses the core library's builders, prompting the user for each configurable value.

## Key Algorithms

### SCID Generation (spec section 3.7.3)

```
Input:  preliminary log entry with {SCID} placeholders
Output: base58btc-encoded SCID string

1. Build preliminary log entry JSON with:
   - versionId = "{SCID}"
   - versionTime = current UTC ISO8601
   - parameters = {method: "did:webvh:1.0", scid: "{SCID}", updateKeys: [...], ...}
   - state = DID Document with {SCID} placeholders
2. JCS-canonicalize the JSON
3. SHA-256 hash the canonicalized bytes
4. Multihash-encode (prepend SHA-256 identifier + length)
5. Base58btc-encode
6. Result is the SCID
```

### Entry Hash Generation (spec section 3.7.4)

```
Input:  log entry (with proof removed, versionId set to predecessor)
Output: base58btc-encoded hash string

1. Take the log entry JSON object
2. Remove the "proof" field
3. Set "versionId" to predecessor (SCID for first entry, previous versionId for subsequent)
4. JCS-canonicalize
5. SHA-256 hash
6. Multihash-encode
7. Base58btc-encode
8. Result is the entryHash
```

### Entry Hash Verification (spec section 3.7.4)

```
Input:  log entry from DID log
Output: valid / invalid

1. Extract versionId, split into version number + entryHash
2. Determine hash algorithm from the multihash entryHash prefix
3. Remove "proof" from entry
4. Set "versionId" to predecessor value (SCID for first, previous versionId for others)
5. JCS-canonicalize -> hash -> multihash -> base58btc
6. Compare with extracted entryHash
```

### Data Integrity Proof (eddsa-jcs-2022)

```
Signing:
1. Remove "proof" from log entry
2. JCS-canonicalize the entry
3. Sign canonicalized bytes with Ed25519 private key
4. Construct proof: {
     type: "DataIntegrityProof",
     cryptosuite: "eddsa-jcs-2022",
     verificationMethod: "did:key:z6Mk...#z6Mk...",
     proofPurpose: "assertionMethod",
     created: "<ISO8601>",
     proofValue: "<multibase-encoded signature>"
   }

Verification:
1. Extract verificationMethod from proof -> extract public key from did:key URI
2. Remove "proof" from log entry
3. JCS-canonicalize
4. Verify Ed25519 signature over canonicalized bytes using extracted public key
```

### Log Chain Validation (spec section 3.6.2)

```
For each log entry (in order):
1. Merge parameters with accumulated active parameters
2. Verify Data Integrity proof is signed by an authorized key (from active updateKeys)
3. Verify versionId format (version_number-entryHash)
4. Verify version number increments by 1
5. Verify entryHash matches calculated hash
6. Verify versionTime > previous versionTime
7. Verify versionTime <= current time (for last entry)
8. For first entry: verify SCID matches calculated SCID
9. Check DID Document id matches the DID being resolved
10. If pre-rotation active: verify updateKeys hashes match previous nextKeyHashes
11. If witnesses active: verify witness proofs meet threshold
12. If entry fails: fall back to last known valid entry
```

### Pre-Rotation Key Hash (spec section 3.7.7)

```
Input:  public key (that will become a future updateKey)
Output: hash string for nextKeyHashes array

1. Generate multikey representation of the public key
2. SHA-256 hash the multikey bytes
3. Multihash-encode
4. Base58btc-encode
```

## Error Handling

Single exception hierarchy, all unchecked:

```
DidWebVhException (RuntimeException)
  +-- ValidationException     // Log chain validation failures
  +-- ResolutionException     // Network errors, HTTP errors, parse errors
  +-- SigningException        // Signer failures
  +-- UrlParseException       // Invalid DID URL format
```

Each exception carries a descriptive message. `ValidationException` includes the entry index and specific validation failure reason. `ResolutionException` includes the URL that failed and HTTP status code if applicable.

## Thread Safety

- All model classes are immutable and thread-safe.
- `DidWebVh` facade methods are stateless and thread-safe.
- `DidWebVhState` is mutable (log entries are appended). It is NOT thread-safe. Callers must synchronize if sharing across threads.
- `Signer` implementations are responsible for their own thread safety.

## Serialization

- **Gson** is used for all JSON operations. Custom `TypeAdapter` classes handle spec-specific formats.
- **JSONL**: `LogEntry` has `toJsonLine()` (compact, no whitespace, no trailing newline) and a static `fromJsonLine()` parser.
- **JCS**: Uses the `java-json-canonicalization` library, which implements RFC 8785.
- **did.jsonl**: The full log is serialized as one JSON object per line. Parsed by splitting on `\n` and parsing each line.
- **did-witness.json**: Standard JSON array of witness proof entries.

## Caching

`DidWebVhState` supports save/load for local caching:

```java
// Save state to JSON
String json = state.toJson();

// Load cached state
DidWebVhState cached = DidWebVhState.fromJson(json);

// Re-validate after loading from cache (the spec requires this)
cached.validate();
```

## Design Decisions Log

| Decision | Rationale |
|----------|-----------|
| Maven over Gradle | More common in enterprise Java; better Central publishing tooling; simpler POM for a library |
| Gson over Jackson | Smaller footprint; no annotation processing; sufficient for our JSON needs; Java 11 compatible |
| OkHttp over java.net.http | Java 11's HttpClient is fine, but OkHttp provides better testing (MockWebServer), interceptors, and is battle-tested in Android/Java ecosystems |
| BouncyCastle over libsodium | Pure Java, no native dependencies, well-maintained, supports Ed25519 and all multikey formats we need |
| Multi-module Maven over single module | Clean separation: core has no CLI deps, signing adapters are pluggable, wizard is optional |
| Unchecked exceptions over checked | Cleaner API, easier to use. Callers who care can catch `DidWebVhException`. |
| Builder pattern for create/update | Matches the Rust implementation's approach; allows step-by-step composition before finalizing with proof |
| No async/reactive | Java 11 doesn't have great async ergonomics. OkHttp handles HTTP fine synchronously. Keep it simple. |
