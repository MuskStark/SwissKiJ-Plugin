package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.infra.JsonStore;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildConfigTest {

    @Test
    void roundTripsThroughJson(@TempDir Path tmp) throws Exception {
        BuildConfig cfg = BuildConfig.defaults();
        cfg.getPython().setVersion("3.12.10");
        cfg.getPython().setPlatform("win_amd64");

        Path file = tmp.resolve("config.json");
        JsonStore.save(cfg, file);
        BuildConfig loaded = JsonStore.load(file, BuildConfig.class);

        assertEquals("3.12.10", loaded.getPython().getVersion());
        assertEquals("win_amd64", loaded.getPython().getPlatform());
        assertTrue(loaded.getDownload().isRecursive());
    }

    @Test
    void defaultsAreSensible() {
        BuildConfig cfg = BuildConfig.defaults();
        assertEquals("output", cfg.getRepository().getOutput());
        assertEquals("wheelhouse", cfg.getRepository().getWheelDir());
        assertEquals("official", cfg.getDownload().getMirror());
    }
}
