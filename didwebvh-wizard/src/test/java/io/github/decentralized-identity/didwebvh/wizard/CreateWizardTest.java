package io.github.decentralizedidentity.didwebvh.wizard;

import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.resolve.DidResolver;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CreateWizardTest {

    @Test
    void createsMinimalDidAndWritesFiles(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "example.com",      // domain
                "",                 // path (blank)
                "1",                // generate key
                "",                 // alsoKnownAs (blank)
                "",                 // extra controllers (blank)
                "",                 // services (blank)
                "n",                // portable
                "n",                // pre-rotation
                "n",                // witnesses
                "",                 // watchers (blank)
                ""                  // TTL (default)
        );

        String did = new CreateWizard(io).run(tmp);

        assertThat(did).startsWith("did:webvh:").contains(":example.com");
        assertThat(Files.exists(tmp.resolve(WizardFiles.DID_LOG))).isTrue();
        assertThat(Files.exists(tmp.resolve(WizardFiles.DID_SECRETS))).isTrue();
        assertThat(Files.exists(tmp.resolve(WizardFiles.NEXT_KEY_SECRETS))).isFalse();

        // Secrets roundtrip through LocalKeySigner
        String secrets = WizardFiles.read(tmp.resolve(WizardFiles.DID_SECRETS));
        LocalKeySigner reloaded = LocalKeySigner.fromJson(secrets);
        assertThat(reloaded.getPublicKeyMultikey()).startsWith("z6Mk");

        // The log resolves and validates
        String log = WizardFiles.read(tmp.resolve(WizardFiles.DID_LOG));
        ResolveResult result = new DidResolver().resolveFromLog(log, did);
        assertThat(result.getError()).isNull();
        assertThat(result.getDidDocument()).isNotNull();
        assertThat(result.getDidDocument().getId()).isEqualTo(did);

        assertThat(DidWebVh.validate(
                Collections.singletonList(LogEntry.fromJsonLine(log.trim())),
                did).isValid()).isTrue();
        assertThat(io.remainingInputs()).isZero();
    }

    @Test
    void createsPortableDidWithPreRotationAndPath(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "issuer.example.com",       // domain
                "dids:alice",               // path
                "1",                        // generate key
                "",                         // alsoKnownAs
                "",                         // extra controllers
                "",                         // services
                "y",                        // portable
                "y",                        // pre-rotation
                "n",                        // witnesses
                "",                         // watchers
                "300"                       // TTL
        );

        String did = new CreateWizard(io).run(tmp);
        assertThat(did).contains(":issuer.example.com:dids:alice");
        assertThat(Files.exists(tmp.resolve(WizardFiles.NEXT_KEY_SECRETS))).isTrue();
    }

    @Test
    void witnessGenerateStoresSecretAndWritesProofs(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "example.com", "",
                "1",                // generate key
                "",                 // alsoKnownAs
                "",                 // extra controllers
                "",                 // services
                "n",                // portable
                "n",                // pre-rotation
                "y",                // configure witnesses
                "1",                // generate new witness (auto-stored locally)
                "6",                // done
                "",                 // threshold (default 1)
                "",                 // watchers
                ""                  // ttl
        );
        String did = new CreateWizard(io).run(tmp);
        assertThat(did).startsWith("did:webvh:");
        Path witnessesDir = tmp.resolve(WizardFiles.WITNESSES_DIR);
        assertThat(witnessesDir).exists();
        assertThat(witnessesDir.toFile().listFiles()).hasSize(1);

        // The generated witness's stored secret must have been used to sign the first
        // entry so the did-witness.json is ready to publish alongside did.jsonl.
        Path witnessFile = tmp.resolve(WizardFiles.DID_WITNESS);
        assertThat(witnessFile).exists();
        String witnessJson = WizardFiles.read(witnessFile);
        assertThat(witnessJson).startsWith("[").contains("\"versionId\"").contains("1-");
    }

    @Test
    void rejectsInvalidServicesJson(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "example.com",
                "",
                "1",
                "",
                "",
                "not-json"                  // services: invalid
        );
        org.junit.jupiter.api.Assertions.assertThrows(WizardException.class,
                () -> new CreateWizard(io).run(tmp));
    }
}
