package io.github.decentralizedidentity.didwebvh.wizard;

import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveWizardTest {

    @Test
    void resolvesFromLocalFile(@TempDir Path tmp) {
        LocalKeySigner signer = LocalKeySigner.generate();
        CreateDidResult created = DidWebVh.create("example.com", signer).execute();
        WizardFiles.write(tmp.resolve(WizardFiles.DID_LOG), created.getLogLine());

        ScriptedWizardIo io = new ScriptedWizardIo(
                "2",                        // local file
                "n",                        // no version filter
                "",                         // accept default file path
                created.getDid()            // expected DID
        );
        new ResolveWizard(io).run(tmp);
        assertThat(io.allOutput()).contains("DID Document:");
        assertThat(io.allOutput()).contains(created.getDid());
        assertThat(io.allOutput()).contains("Resolution metadata:");
        assertThat(io.errors()).isEmpty();
    }

    @Test
    void reportsErrorWhenFileMissing(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "2",
                "n",
                tmp.resolve("missing.jsonl").toString(),
                ""
        );
        org.junit.jupiter.api.Assertions.assertThrows(WizardException.class,
                () -> new ResolveWizard(io).run(tmp));
    }
}
