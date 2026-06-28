package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.ui.ProjectContext;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectContextTest {

    @Test
    void openExistingLoadsConfig(@TempDir Path tmp) throws Exception {
        new plugin.swisskit.offlinepython.command.InitService().initialize(tmp);
        ProjectContext ctx = new ProjectContext();
        ctx.openExisting(tmp);
        assertTrue(ctx.hasProject());
        assertEquals(tmp, ctx.projectDirProperty().get());
        assertNotNull(ctx.configProperty().get());
        assertEquals("3.12.10", ctx.configProperty().get().getPython().getVersion());
    }

    @Test
    void hasProjectFalseUntilOpened() {
        ProjectContext ctx = new ProjectContext();
        assertFalse(ctx.hasProject());
    }

    @Test
    void createNewInitializesAndSetsProject(@TempDir Path tmp) throws Exception {
        ProjectContext ctx = new ProjectContext();
        ctx.createNew(tmp);
        assertTrue(ctx.hasProject());
        assertTrue(java.nio.file.Files.exists(tmp.resolve("config.json")));
    }
}
