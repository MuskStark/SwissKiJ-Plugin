package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {

    @Test
    void buildsPipDownloadCommandForWindowsTarget() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "/usr/local/bin/python3.12", "requirements.txt", "output/wheelhouse",
            "win_amd64", "3.12", "cp", true);
        assertEquals(List.of(
            "/usr/local/bin/python3.12", "-m", "pip", "download",
            "-r", "requirements.txt",
            "-d", "output/wheelhouse",
            "--platform", "win_amd64",
            "--python-version", "3.12",
            "--implementation", "cp",
            "--only-binary=:all:"
        ), cmd);
    }

    @Test
    void omitsOnlyBinaryFlagWhenFalse() {
        List<String> cmd = ProcessRunner.pipDownloadCommand(
            "python3", "r.txt", "wh", "linux_x86_64", "3.12", "cp", false);
        assertFalse(cmd.contains("--only-binary=:all:"));
        assertTrue(cmd.contains("--platform"));
        assertTrue(cmd.contains("linux_x86_64"));
    }
}
