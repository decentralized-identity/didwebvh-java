# did:scid vh:1 logs

Logs whose entries name the DID as `did:scid:vh:1:<SCID>` rather than as a
did:webvh, for `ResolveOptions.Builder#didScidIds(boolean)` (`DidScidIdsTest`).
Apart from the id, each follows did:webvh 1.0: SCID, entry hashes,
`eddsa-jcs-2022` proofs by the update key (a fixed Ed25519 key), and
`versionTime` from 2026-09-29.

They were made by an independent implementation (LocalPub's
`scripts/vh1-vectors.mjs`, in JavaScript), not by this library.

| File                         | Expected with `didScidIds(true)`                                   |
|------------------------------|--------------------------------------------------------------------|
| `scid-genesis.jsonl`         | Resolves: one entry                                                |
| `scid-three-entries.jsonl`   | Resolves to entry 3 (`alsoKnownAs: ["urn:example:entry-3"]`)       |
| `scid-with-host.jsonl`       | Refused: entry 2's id names a host after the SCID                  |
| `scid-with-query.jsonl`      | Refused: entry 2's id has a query                                  |
| `scid-with-fragment.jsonl`   | Refused: entry 2's id has a fragment                               |
| `scid-other-scid.jsonl`      | Refused: entry 2's id names another SCID                           |
| `scid-moves-to-webvh.jsonl`  | Refused: entry 2 names a did:webvh                                 |
| `webvh-moves-to-scid.jsonl`  | Refused: entry 1 names a did:webvh                                 |
