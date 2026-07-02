package plugin.swisskit.offlinepython.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import plugin.swisskit.offlinepython.domain.DeployTarget;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeployServiceTest {

    /** 用一个全平台兼容的纯 Python wheel 构造 bundle ZIP。 */
    private Path makeBundle(Path tmp) throws IOException {
        Path zip = tmp.resolve("b.zip");
        Manifest m = new Manifest();
        m.getPython().setVersion("3.12.10");
        m.getWheels().add(new WheelEntry("requests", "2.31.0",
                "wheels/requests-2.31.0-py3-none-any.whl", "abc", 1000, true));
        String json = JsonStore.toJson(m);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("bundle/manifest.json"));
            z.write(json.getBytes());
            z.closeEntry();
            z.putNextEntry(new ZipEntry("bundle/wheels/requests-2.31.0-py3-none-any.whl"));
            z.write(new byte[]{1});
            z.closeEntry();
        }
        return zip;
    }

    @Test
    void installRunsPipPerWheel(@TempDir Path tmp) throws Exception {
        Path zip = makeBundle(tmp);
        // mock ProcessRunner:所有命令返回 0
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(anyList(), any())).thenReturn(0);

        DeployTarget target = new DeployTarget.Global(Path.of("/usr/bin/python3"));
        List<String> logs = new ArrayList<>();
        Consumer<String> onLog = logs::add;

        DeployResult r = new DeployService(runner).install(zip, target, onLog);

        assertEquals(1, r.installed());
        assertEquals(0, r.failed());
        // 至少调用了一次 pip install
        verify(runner, atLeastOnce()).run(anyList(), any());
    }

    @Test
    void failedWheelDoesNotAbortOthers(@TempDir Path tmp) throws Exception {
        Path zip = makeBundle(tmp);
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(anyList(), any())).thenReturn(1); // 全失败

        DeployTarget target = new DeployTarget.Global(Path.of("/usr/bin/python3"));
        DeployResult r = new DeployService(runner).install(zip, target, s -> {});

        assertEquals(0, r.installed());
        assertEquals(1, r.failed());
    }
}
