package io.github.decentralizedidentity.didwebvh.wizard;

import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared witness configuration helpers used by both Create and Update flows.
 *
 * <p>Witness secrets the user has access to locally are stored under
 * {@code <workDir>/witnesses/witness-&lt;multikey&gt;.json}.  A witness is always
 * identified in the DID log by its {@code did:key:&lt;multikey&gt;} URL; the
 * accompanying secret is optional – if the local store has it the wizard can
 * produce a Data Integrity proof for that witness automatically.
 */
final class WizardWitnessKeys {

    private final WizardIo io;
    private final WizardPrompts ask;

    WizardWitnessKeys(WizardIo io) {
        this.io = io;
        this.ask = new WizardPrompts(io);
    }

    /** Path to the witness key store inside {@code workDir}. */
    static Path storeDir(Path workDir) {
        return workDir.resolve(WizardFiles.WITNESSES_DIR);
    }

    /** File name used to save a witness signer JSON to the local store. */
    static Path storeFile(Path workDir, String multikey) {
        return storeDir(workDir).resolve("witness-" + multikey + ".json");
    }

    /** List all witness signers currently kept in the local store. */
    static List<LocalKeySigner> listStored(Path workDir) {
        Path dir = storeDir(workDir);
        List<LocalKeySigner> signers = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return signers;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "witness-*.json")) {
            for (Path p : stream) {
                signers.add(LocalKeySigner.fromJson(WizardFiles.read(p)));
            }
        } catch (IOException e) {
            throw new WizardException("Failed to list witness key store: "
                    + e.getMessage(), e);
        }
        return signers;
    }

    /** Look up a stored witness signer by its multikey, or {@code null} if absent. */
    static LocalKeySigner findByMultikey(Path workDir, String multikey) {
        Path file = storeFile(workDir, multikey);
        if (!Files.exists(file)) {
            return null;
        }
        return LocalKeySigner.fromJson(WizardFiles.read(file));
    }

    /** Persist {@code signer} as a witness secret under {@code workDir/witnesses/}. */
    static void save(Path workDir, LocalKeySigner signer) {
        Path dir = storeDir(workDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new WizardException("Failed to create " + dir + ": " + e.getMessage(), e);
        }
        WizardFiles.write(storeFile(workDir, signer.getPublicKeyMultikey()), signer.toJson());
    }

    /** Build a witness config from scratch (no pre-existing witnesses). */
    WitnessConfig configure(Path workDir) {
        return configure(workDir, null);
    }

    /**
     * Interactive witness-set builder used by create and update.
     *
     * <p>When {@code current} is non-null its entries are kept as the starting set, so the
     * user only needs to add, remove, or keep as-is.  For each action the user can:
     * <ol>
     *   <li>Generate a new Ed25519 key now and save its secret locally;</li>
     *   <li>Pick one of the already-stored witness secrets;</li>
     *   <li>Import a LocalKeySigner JSON from a path (copied into the local store);</li>
     *   <li>Enter a {@code did:key:...} directly (no local secret – proofs must
     *       come from the witness later);</li>
     *   <li>Remove an existing witness from the set.</li>
     * </ol>
     */
    WitnessConfig configure(Path workDir, WitnessConfig current) {
        List<WitnessEntry> entries = new ArrayList<>();
        if (current != null && current.getWitnesses() != null) {
            entries.addAll(current.getWitnesses());
        }
        io.println("Configure witnesses – add/remove entries, then choose 'Done'.");
        while (true) {
            io.println("");
            printCurrentSet(entries);
            io.println("  1) Generate a new witness key (stored locally)");
            io.println("  2) Use an existing stored witness secret");
            io.println("  3) Import a witness secret from a path (copied locally)");
            io.println("  4) Enter a did:key for an external witness");
            io.println("  5) Remove an existing witness");
            io.println("  6) Done");
            int choice = ask.askChoice("Choose 1-6: ", 6);
            if (choice == 6) {
                break;
            }
            if (choice == 5) {
                removeOne(entries);
                continue;
            }
            WitnessEntry added = addOne(choice, workDir, entries);
            if (added != null) {
                entries.add(added);
                io.println("Added witness " + added.getId());
            }
        }
        if (entries.isEmpty()) {
            throw new WizardException("At least one witness is required "
                    + "(or skip witness configuration).");
        }
        int defaultThreshold = currentThresholdOrMajority(current, entries.size());
        int threshold = ask.askInt(
                "Witness threshold [default " + defaultThreshold
                        + ", max " + entries.size() + "]: ",
                defaultThreshold);
        if (threshold < 1 || threshold > entries.size()) {
            throw new WizardException("Threshold must be between 1 and " + entries.size());
        }
        return new WitnessConfig(threshold, entries);
    }

    private void printCurrentSet(List<WitnessEntry> entries) {
        if (entries.isEmpty()) {
            io.println("Current witnesses: (none)");
            return;
        }
        io.println("Current witnesses (" + entries.size() + "):");
        for (WitnessEntry entry : entries) {
            io.println("  - " + entry.getId());
        }
        io.println("");
        io.println("Actions:");
    }

    private void removeOne(List<WitnessEntry> entries) {
        if (entries.isEmpty()) {
            io.printError("No witnesses to remove.");
            return;
        }
        io.println("Select a witness to remove:");
        for (int i = 0; i < entries.size(); i++) {
            io.println("  [" + (i + 1) + "] " + entries.get(i).getId());
        }
        int idx = ask.askChoice(
                "Remove which witness (1-" + entries.size() + ")? ", entries.size());
        WitnessEntry removed = entries.remove(idx - 1);
        io.println("Removed " + removed.getId());
    }

    private int currentThresholdOrMajority(WitnessConfig current, int size) {
        int majority = (size / 2) + 1;
        if (current == null) {
            return majority;
        }
        int existing = current.getThreshold();
        return existing >= 1 && existing <= size ? existing : majority;
    }

    private WitnessEntry addOne(int choice, Path workDir, List<WitnessEntry> alreadyAdded) {
        switch (choice) {
            case 1: {
                LocalKeySigner signer = LocalKeySigner.generate();
                save(workDir, signer);
                io.println("Generated witness key: "
                        + WizardWitnessKeys.storeFile(workDir, signer.getPublicKeyMultikey()));
                return asEntry(signer, alreadyAdded);
            }
            case 2: {
                List<LocalKeySigner> stored = listStored(workDir);
                if (stored.isEmpty()) {
                    io.printError("No stored witness keys found in "
                            + storeDir(workDir).toAbsolutePath());
                    return null;
                }
                for (int i = 0; i < stored.size(); i++) {
                    io.println("  " + (i + 1) + ") did:key:"
                            + stored.get(i).getPublicKeyMultikey());
                }
                int idx = ask.askChoice(
                        "Pick a stored key 1-" + stored.size() + ": ", stored.size());
                return asEntry(stored.get(idx - 1), alreadyAdded);
            }
            case 3: {
                String pathStr = ask.askRequired("Path to witness signer JSON: ");
                LocalKeySigner signer;
                try {
                    signer = LocalKeySigner.fromJson(WizardFiles.read(Path.of(pathStr)));
                } catch (RuntimeException e) {
                    io.printError("Could not load witness key: " + e.getMessage());
                    return null;
                }
                save(workDir, signer);
                io.println("Copied witness secret to local store.");
                return asEntry(signer, alreadyAdded);
            }
            case 4: {
                String didKey = ask.askRequired("Witness did:key URL: ");
                if (!didKey.startsWith("did:key:")) {
                    io.printError("Must start with did:key: – got " + didKey);
                    return null;
                }
                if (containsId(alreadyAdded, didKey)) {
                    io.printError("Duplicate witness: " + didKey);
                    return null;
                }
                return new WitnessEntry(didKey);
            }
            default:
                return null;
        }
    }

    private WitnessEntry asEntry(LocalKeySigner signer, List<WitnessEntry> alreadyAdded) {
        String did = "did:key:" + signer.getPublicKeyMultikey();
        if (containsId(alreadyAdded, did)) {
            io.printError("Duplicate witness: " + did);
            return null;
        }
        return new WitnessEntry(did);
    }

    private boolean containsId(List<WitnessEntry> list, String id) {
        for (WitnessEntry w : list) {
            if (id.equals(w.getId())) {
                return true;
            }
        }
        return false;
    }
}
