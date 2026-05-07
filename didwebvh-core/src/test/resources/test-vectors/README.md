# did:webvh Test Vectors

Each file in this directory is a canonical input for the spec-compliance
integration tests in `SpecComplianceIT`. Files are produced by running the
library's own `create`/`update`/`migrate`/`deactivate` operations with
deterministic Ed25519 seeds (see `TestVectors` and `TestVectorGenerator`
under `src/test/java/.../integration/`).

| File                              | What it exercises                                              |
|-----------------------------------|----------------------------------------------------------------|
| `first-log-entry-good.jsonl`      | Minimal valid single-entry DID log                             |
| `first-log-entry-tampered.jsonl`  | Same log with the DID id tampered; must fail validation        |
| `multi-entry-log.jsonl`           | 3-version log with witness configuration                       |
| `multi-entry-witness.json`        | Witness proofs for each version above                          |
| `deactivated-did.jsonl`           | Create + deactivate; ends with `deactivated=true`, no keys     |
| `migrated-did.jsonl`              | Portable create + migrate to `new.example.com`                 |
| `pre-rotation-log.jsonl`          | Create with `nextKeyHashes`, then legitimate rotation          |

## Regenerating

```
./mvnw -pl didwebvh-core test-compile
java -cp "$(./mvnw -pl didwebvh-core -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout):didwebvh-core/target/classes:didwebvh-core/target/test-classes" \
    io.github.decentralizedidentity.didwebvh.core.integration.TestVectorGenerator
```

The seeds are fixed, but `versionTime` uses the wall clock — every
regeneration will produce new SCIDs and entry hashes. Commit the refreshed
files in the same change as the logic they reflect.
