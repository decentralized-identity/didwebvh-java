package io.github.decentralizedidentity.didwebvh.wizard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportDidWebWizardTest {

    @Test
    void writesParallelDidWebDocumentForCreatedDid(@TempDir Path tmp) {
        LocalKeySigner signer = LocalKeySigner.generate();
        CreateDidResult created = DidWebVh.create("example.com", signer).execute();
        WizardFiles.write(tmp.resolve(WizardFiles.DID_LOG), created.getLogLine());

        ScriptedWizardIo io = new ScriptedWizardIo("");  // accept default filename
        new ExportDidWebWizard(io).run(tmp);

        Path outPath = tmp.resolve(ExportDidWebWizard.DEFAULT_OUTPUT);
        assertThat(Files.exists(outPath)).isTrue();

        JsonObject doc = JsonParser.parseString(WizardFiles.read(outPath)).getAsJsonObject();
        assertThat(doc.get("id").getAsString()).startsWith("did:web:example.com");
        JsonArray aka = doc.getAsJsonArray("alsoKnownAs");
        boolean containsOriginal = false;
        for (JsonElement el : aka) {
            if (created.getDid().equals(el.getAsString())) {
                containsOriginal = true;
            }
        }
        assertThat(containsOriginal).isTrue();
    }

    @Test
    void errorsWhenNoLogInDirectory(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo();
        assertThatThrownBy(() -> new ExportDidWebWizard(io).run(tmp))
                .isInstanceOf(WizardException.class)
                .hasMessageContaining(WizardFiles.DID_LOG);
    }
}
