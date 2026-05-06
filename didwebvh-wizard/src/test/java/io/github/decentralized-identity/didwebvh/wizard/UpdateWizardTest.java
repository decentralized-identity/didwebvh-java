package io.github.decentralizedidentity.didwebvh.wizard;

import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateWizardTest {

    private static CreateDidResult seedDid(Path tmp, boolean portable) {
        LocalKeySigner signer = LocalKeySigner.generate();
        CreateDidResult result = DidWebVh.create("example.com", signer)
                .portable(portable)
                .execute();
        WizardFiles.write(tmp.resolve(WizardFiles.DID_LOG), result.getLogLine());
        WizardFiles.write(tmp.resolve(WizardFiles.DID_SECRETS), signer.toJson());
        return result;
    }

    @Test
    void modifyAppendsNewEntryWithChangedTtl(@TempDir Path tmp) {
        seedDid(tmp, false);
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",                 // modify
                "n",                 // don't replace document
                "y",                 // change parameters
                "n",                 // updateKeys
                "n",                 // nextKeyHashes
                "n",                 // witness
                "n",                 // watchers
                "y",                 // ttl
                "120"                // new TTL
        );

        new UpdateWizard(io).run(tmp);

        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        String[] lines = log.split("\n");
        assertThat(lines).hasSize(2);

        DidWebVhState reloaded = DidWebVhState.fromDidLog(
                extractDid(lines[0]), log);
        ValidationResult res = reloaded.validate();
        assertThat(res.isValid()).isTrue();
        assertThat(reloaded.getActiveParameters().getTtl()).isEqualTo(120);
    }

    @Test
    void migrateRequiresPortable(@TempDir Path tmp) {
        seedDid(tmp, false);
        ScriptedWizardIo io = new ScriptedWizardIo("2");
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining("portable");
    }

    @Test
    void migrateAppendsEntryWhenPortable(@TempDir Path tmp) {
        seedDid(tmp, true);
        ScriptedWizardIo io = new ScriptedWizardIo(
                "2",                        // migrate
                "new.example.com",          // new domain
                ""                          // no path
        );
        new UpdateWizard(io).run(tmp);
        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        assertThat(log.split("\n")).hasSize(2);
        assertThat(log).contains("new.example.com");
    }

    @Test
    void deactivateRequiresConfirmation(@TempDir Path tmp) {
        seedDid(tmp, false);
        ScriptedWizardIo io = new ScriptedWizardIo(
                "3",
                "nope"            // wrong confirmation
        );
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining("not confirmed");
    }

    @Test
    void deactivateAppendsFinalEntry(@TempDir Path tmp) {
        seedDid(tmp, false);
        ScriptedWizardIo io = new ScriptedWizardIo(
                "3",
                "DEACTIVATE"
        );
        new UpdateWizard(io).run(tmp);
        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        DidWebVhState state = DidWebVhState.fromDidLog(extractDid(log.split("\n")[0]), log);
        assertThat(state.validate().isValid()).isTrue();
        assertThat(state.isDeactivated()).isTrue();
    }

    @Test
    void errorsWhenNoLogInDirectory(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo();
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining(WizardFiles.DID_LOG);
    }

    @Test
    void replaceDocumentRejectsScidChange(@TempDir Path tmp) {
        seedDid(tmp, false);
        // 46 base58btc chars, different from any generated SCID.
        String otherScid = "Qmaaaaaaaaaabbbbbbbbbbccccccccccddddddddddeeee";
        String badDoc = "{\"id\":\"did:webvh:" + otherScid + ":example.com\"}";
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",    // modify
                "y",    // replace document
                badDoc
        );
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining("SCID");
    }

    @Test
    void replaceDocumentRejectsDomainChangeWhenNotPortable(@TempDir Path tmp) {
        CreateDidResult created = seedDid(tmp, false);
        String scid = created.getDid().split(":")[2];
        String badDoc = "{\"id\":\"did:webvh:" + scid + ":other.example.com\"}";
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",
                "y",
                badDoc
        );
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining("not portable");
    }

    @Test
    void replaceDocumentRejectsDomainChangeSuggestsMigrateWhenPortable(@TempDir Path tmp) {
        CreateDidResult created = seedDid(tmp, true);
        String scid = created.getDid().split(":")[2];
        String badDoc = "{\"id\":\"did:webvh:" + scid + ":other.example.com\"}";
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",
                "y",
                badDoc
        );
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining("Migrate");
    }

    @Test
    void replaceDocumentRequiresIdField(@TempDir Path tmp) {
        seedDid(tmp, false);
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",
                "y",
                "{\"foo\":\"bar\"}"
        );
        assertThatThrownBy(() -> new UpdateWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining("missing required 'id' field");
    }

    @Test
    void changeWatchersParameter(@TempDir Path tmp) {
        seedDid(tmp, false);
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",                           // modify
                "n",                           // don't replace doc
                "y",                           // change params
                "n",                           // updateKeys
                "n",                           // nextKeyHashes
                "n",                           // witness
                "y",                           // watchers
                "https://watch.example.com",   // watchers value
                "n"                            // ttl
        );
        new UpdateWizard(io).run(tmp);
        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        DidWebVhState reloaded = DidWebVhState.fromDidLog(
                extractDid(log.split("\n")[0]), log);
        assertThat(reloaded.validate().isValid()).isTrue();
        assertThat(reloaded.getActiveParameters().getWatchers())
                .containsExactly("https://watch.example.com");
    }

    @Test
    void changeWatchersAppendsToExistingList(@TempDir Path tmp) {
        LocalKeySigner signer = LocalKeySigner.generate();
        CreateDidResult created = DidWebVh.create("example.com", signer)
                .watchers(Collections.singletonList("https://first.example.com"))
                .execute();
        WizardFiles.write(tmp.resolve(WizardFiles.DID_LOG), created.getLogLine());
        WizardFiles.write(tmp.resolve(WizardFiles.DID_SECRETS), signer.toJson());

        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",                              // modify
                "n",                              // don't replace doc
                "y",                              // change params
                "n", "n", "n",                    // updateKeys, nextKeyHashes, witness
                "y",                              // watchers
                "https://second.example.com",     // append
                "n"                               // ttl
        );
        new UpdateWizard(io).run(tmp);
        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        DidWebVhState reloaded = DidWebVhState.fromDidLog(
                extractDid(log.split("\n")[0]), log);
        assertThat(reloaded.validate().isValid()).isTrue();
        assertThat(reloaded.getActiveParameters().getWatchers())
                .containsExactly("https://first.example.com", "https://second.example.com");
    }

    @Test
    void changeWitnessesSeedsFromExistingConfig(@TempDir Path tmp) {
        LocalKeySigner controller = LocalKeySigner.generate();
        LocalKeySigner witnessKey = LocalKeySigner.generate();
        WitnessConfig witness = new WitnessConfig(1, Collections.singletonList(
                new WitnessEntry("did:key:" + witnessKey.getPublicKeyMultikey())));
        CreateDidResult created = DidWebVh.create("example.com", controller)
                .witness(witness)
                .execute();
        WizardFiles.write(tmp.resolve(WizardFiles.DID_LOG), created.getLogLine());
        WizardFiles.write(tmp.resolve(WizardFiles.DID_SECRETS), controller.toJson());
        Path witnessKeyFile = tmp.resolve("witness-key.json");
        WizardFiles.write(witnessKeyFile, witnessKey.toJson());

        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",                             // modify
                "n",                             // don't replace doc
                "y",                             // change params
                "n", "n",                        // updateKeys, nextKeyHashes
                "y",                             // witness
                "n",                             // don't clear witnesses
                "1",                             // generate a new (second) witness
                "6",                             // done
                "2",                             // threshold = 2 (needs seeded existing entry)
                "n",                             // watchers
                "n",                             // ttl
                witnessKeyFile.toString()        // existing witness signs the new entry
        );
        new UpdateWizard(io).run(tmp);

        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        String[] lines = log.split("\n");
        assertThat(lines).hasSize(2);
        DidWebVhState reloaded = DidWebVhState.fromDidLog(
                extractDid(lines[0]), log);
        Parameters active = reloaded.accumulateParameters();
        assertThat(active.getWitness().getThreshold()).isEqualTo(2);
        assertThat(active.getWitness().getWitnesses()).hasSize(2);
    }

    @Test
    void updateWithActiveWitnessCollectsProofs(@TempDir Path tmp) {
        LocalKeySigner controller = LocalKeySigner.generate();
        LocalKeySigner witnessKey = LocalKeySigner.generate();
        WitnessConfig witness = new WitnessConfig(1, Collections.singletonList(
                new WitnessEntry("did:key:" + witnessKey.getPublicKeyMultikey())));
        CreateDidResult created = DidWebVh.create("example.com", controller)
                .witness(witness)
                .execute();
        WizardFiles.write(tmp.resolve(WizardFiles.DID_LOG), created.getLogLine());
        WizardFiles.write(tmp.resolve(WizardFiles.DID_SECRETS), controller.toJson());
        Path witnessKeyFile = tmp.resolve("witness-key.json");
        WizardFiles.write(witnessKeyFile, witnessKey.toJson());

        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",                                    // modify
                "n",                                    // don't replace doc
                "y",                                    // change params
                "n", "n", "n", "n",                     // updateKeys, nextKeyHashes, witness, watchers
                "y",                                    // ttl
                "60",
                witnessKeyFile.toString()               // witness signer key
        );
        new UpdateWizard(io).run(tmp);

        Path witnessFile = tmp.resolve(WizardFiles.DID_WITNESS);
        assertThat(witnessFile).exists();
        String witnessJson = WizardFiles.read(witnessFile);
        assertThat(witnessJson).startsWith("[");
        assertThat(witnessJson).contains("2-");
    }

    private static String extractDid(String line) {
        int idx = line.indexOf("\"id\":\"");
        int start = idx + 6;
        int end = line.indexOf("\"", start);
        return line.substring(start, end);
    }
}
