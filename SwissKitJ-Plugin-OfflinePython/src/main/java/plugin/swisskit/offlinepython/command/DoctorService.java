package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.infra.PythonDetector;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Diagnoses the host environment for build readiness. */
public class DoctorService {

    public record Check(String name, String value, boolean ok) {}

    public List<Check> run(String configuredExecutable) {
        List<Check> out = new ArrayList<>();
        PythonDetector.Detection d = PythonDetector.detect(configuredExecutable);
        out.add(new Check("Python interpreter", d.executable() == null ? "not found" : d.executable(), d.executable() != null));
        out.add(new Check("Python version", d.pythonVersion() == null ? "—" : d.pythonVersion(),
                d.pythonVersion() != null && PythonDetector.isAtLeast(d.pythonVersion(), "3.10")));
        out.add(new Check("pip", d.pipVersion() == null ? "missing" : d.pipVersion(), d.pipVersion() != null));
        long freeGb = freeSpaceGb(Path.of(System.getProperty("user.home")));
        out.add(new Check("Free disk", freeGb + " GB", freeGb > 1));
        Path cache = Path.of(System.getProperty("user.home"), ".offline-python", "cache");
        out.add(new Check("Cache dir writable", cache.toString(), isWritable(cache)));
        return out;
    }

    private boolean isWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (Exception e) {
            return false;
        }
    }

    private long freeSpaceGb(Path p) {
        try {
            FileStore store = Files.getFileStore(p);
            return store.getUsableSpace() / (1024L * 1024 * 1024);
        } catch (Exception e) {
            return 0;
        }
    }
}
