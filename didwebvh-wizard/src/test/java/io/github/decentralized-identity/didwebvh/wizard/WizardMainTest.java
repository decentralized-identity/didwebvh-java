package io.github.decentralizedidentity.didwebvh.wizard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WizardMainTest {

    @Test
    void menuDispatchesToCreateAndExits(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "1",                        // menu: create
                "example.com",              // domain
                "",                         // path
                "1",                        // generate key
                "",                         // alsoKnownAs
                "",                         // controllers
                "",                         // services
                "n",                        // portable
                "n",                        // pre-rotation
                "n",                        // witnesses
                "",                         // watchers
                "",                         // ttl default
                "5"                         // menu: exit
        );
        WizardMain main = new WizardMain();
        main.setWorkDir(tmp);
        assertThat(main.run(io)).isZero();
        assertThat(Files.exists(tmp.resolve(WizardFiles.DID_LOG))).isTrue();
    }

    @Test
    void actionModeRunsSingleAction(@TempDir Path tmp) {
        ScriptedWizardIo io = new ScriptedWizardIo(
                "example.com", "", "1", "", "", "", "n", "n", "n", "", ""
        );
        WizardMain main = new WizardMain();
        main.setWorkDir(tmp);
        main.setAction("create");
        assertThat(main.run(io)).isZero();
        assertThat(Files.exists(tmp.resolve(WizardFiles.DID_LOG))).isTrue();
    }
}
