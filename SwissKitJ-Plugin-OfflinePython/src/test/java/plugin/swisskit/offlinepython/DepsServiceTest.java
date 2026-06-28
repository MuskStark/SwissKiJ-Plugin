package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.command.DepsService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DepsServiceTest {

    @Test
    void parsesLatestVersionFromPipIndexVersions() {
        String out = "numpy (1.26.4)\nAvailable versions: 1.26.4, 1.26.3, 1.26.2";
        assertEquals(Optional.of("1.26.4"), DepsService.parseLatestVersion(out));
    }

    @Test
    void parseLatestVersionEmptyOnGarbage() {
        assertTrue(DepsService.parseLatestVersion("no versions found").isEmpty());
        assertTrue(DepsService.parseLatestVersion((String) null).isEmpty());
    }

    @Test
    void parsesWheelSizeFromPypiJsonForPlatform() {
        String json = """
            { "urls": [
              {"filename":"numpy-1.26.4-cp312-cp312-win_amd64.whl","size":19098624},
              {"filename":"numpy-1.26.4.tar.gz","size":1000}
            ]}
            """;
        long size = DepsService.parsePyPIWheelSize(json, "win_amd64", "1.26.4");
        assertEquals(19098624L, size);
    }

    @Test
    void parsePyPIWheelSizeFallsBackToZero() {
        assertEquals(0L, DepsService.parsePyPIWheelSize("{}", "win_amd64", "1.26.4"));
        assertEquals(0L, DepsService.parsePyPIWheelSize((String) null, "win_amd64", "1.26.4"));
    }

    @Test
    void parsePyPIWheelSizePicksAnyWheelIfPlatformMisses() {
        String json = """
            { "urls": [
              {"filename":"numpy-1.26.4-cp312-cp312-manylinux.whl","size":17000000},
              {"filename":"numpy-1.26.4.tar.gz","size":1000}
            ]}
            """;
        assertEquals(17000000L, DepsService.parsePyPIWheelSize(json, "win_amd64", "1.26.4"));
    }
}
