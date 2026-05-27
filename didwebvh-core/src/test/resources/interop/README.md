# Interop Test Vectors

Vendored from the [did:webvh test suite](https://github.com/swcurran/didwebvh-test-suite).

Upstream commit: `c11fda313dcc8ef0d1d50cff907fa754f33df73e` (2026-05-26)

Each subdirectory tracks one interop regression discovered by the
multi-implementation test suite (see GitHub issue #2). Files are copied
verbatim from the upstream `vectors/` tree and exercised by JUnit tests under
`didwebvh-core/src/test/java/.../interop/`.

| Directory | Source path in upstream | Exercises |
| --- | --- | --- |
| `basic-create-python/`     | `vectors/basic-create/python/`     | Empty `witness: {}` deserialization (no NPE) |
| `basic-update-ts/`         | `vectors/basic-update/ts/`         | TS log with `witness: {}` + `nextKeyHashes: []` + `watchers: []` round-trip (SCID, entry hash, proof) |
| `basic-update-java-eecc/`  | `vectors/basic-update/java-eecc/`  | java-eecc log validation |
| `deactivate-java-eecc/`    | `vectors/deactivate/java-eecc/`    | java-eecc deactivate log validation |
| `key-rotation-java-eecc/`  | `vectors/key-rotation/java-eecc/`  | java-eecc key-rotation log validation |
| `multi-update-java-eecc/`  | `vectors/multi-update/java-eecc/`  | java-eecc multi-update log validation |
| `services-java-eecc/`      | `vectors/services/java-eecc/`      | java-eecc services log validation |
| `witness-update-java-eecc/`| `vectors/witness-update/java-eecc/`| java-eecc witness-update log validation |
| `pre-rotation-consume-rust/`     | `vectors/pre-rotation-consume/rust/`     | Pre-rotation: each entry signed by its own (committed) updateKeys |
| `pre-rotation-consume-java-eecc/`| `vectors/pre-rotation-consume/java-eecc/`| Pre-rotation: each entry signed by its own (committed) updateKeys |
| `witness-update-rust/`           | `vectors/witness-update/rust/`           | Witness proof pruning: single latest proof covers all prior entries |
| `witness-threshold-rust/`        | `vectors/witness-threshold/rust/`        | Witness id as bare multikey (without `did:key:` prefix) |

To refresh: bump the SHA above, re-download the listed files
(`curl -sSL https://raw.githubusercontent.com/swcurran/didwebvh-test-suite/<sha>/<path>`),
run `./mvnw -pl didwebvh-core verify`, and reconcile any test deltas.
