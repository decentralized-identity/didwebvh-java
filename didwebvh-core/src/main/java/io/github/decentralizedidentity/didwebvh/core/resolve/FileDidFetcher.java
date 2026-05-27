package io.github.decentralizedidentity.didwebvh.core.resolve;

import io.github.decentralizedidentity.didwebvh.core.ResolutionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads did:webvh resolution artifacts from the local filesystem. */
class FileDidFetcher {

    String fetchDidLog(Path filePath) {
        return read(filePath, "did log");
    }

    String fetchWitnessProofs(Path witnessPath) {
        return read(witnessPath, "witness proofs");
    }

    private String read(Path path, String label) {
        if (path == null) {
            throw new ResolutionException(label + " path is required", "invalidDid");
        }
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResolutionException("Unable to read " + label + ": " + path,
                    "notFound", e);
        }
    }
}
