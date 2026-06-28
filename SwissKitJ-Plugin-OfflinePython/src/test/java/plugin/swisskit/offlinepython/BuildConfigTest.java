package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.JsonStore;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildConfigTest {

    @Test
    void roundTripsThroughJson(@TempDir Path tmp) throws Exception {
        BuildConfig cfg = BuildConfig.defaults();
        cfg.getPython().setVersion("3.12.10");
        cfg.getPython().setPlatforms(new java.util.ArrayList<>(List.of("win_amd64", "manylinux2014_x86_64")));

        Path file = tmp.resolve("config.json");
        JsonStore.save(cfg, file);
        BuildConfig loaded = JsonStore.load(file, BuildConfig.class);

        assertEquals("3.12.10", loaded.getPython().getVersion());
        assertEquals(List.of("win_amd64", "manylinux2014_x86_64"), loaded.getPython().getPlatforms());
        assertEquals("win_amd64", loaded.getPython().getPrimaryPlatform());
        assertTrue(loaded.getDownload().isRecursive());
    }

    @Test
    void primaryPlatformDefaultsToWinAmd64WhenEmpty() {
        BuildConfig cfg = new BuildConfig();
        cfg.getPython().setPlatforms(new java.util.ArrayList<>());
        assertEquals("win_amd64", cfg.getPython().getPrimaryPlatform());
    }

    @Test
    void defaultsAreSensible() {
        BuildConfig cfg = BuildConfig.defaults();
        assertEquals("output", cfg.getRepository().getOutput());
        assertEquals("wheelhouse", cfg.getRepository().getWheelDir());
        assertEquals("official", cfg.getDownload().getMirror());
    }

    @Test
    void loadsLegacySinglePlatformConfigGracefully() {
        // Old config.json had a single "platform" key (field now removed). Gson ignores the
        // unknown key and `platforms` falls back to its field-initializer default ["win_amd64"]:
        // no crash, valid list. (Non-default legacy platform values are NOT migrated — accepted
        // on this unreleased branch where the UI never persisted a user-chosen platform.)
        String legacyJson = "{\"python\":{\"version\":\"3.12.10\",\"platform\":\"win_amd64\"}}";
        BuildConfig loaded = JsonStore.fromJson(legacyJson, BuildConfig.class);
        assertEquals(List.of("win_amd64"), loaded.getPython().getPlatforms());
        assertEquals("win_amd64", loaded.getPython().getPrimaryPlatform());
    }
}
