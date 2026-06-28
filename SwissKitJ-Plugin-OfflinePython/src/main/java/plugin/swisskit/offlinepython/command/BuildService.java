package plugin.swisskit.offlinepython.command;

import plugin.swisskit.offlinepython.domain.BuildConfig;
import plugin.swisskit.offlinepython.domain.DependencySpec;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.HashUtil;
import plugin.swisskit.offlinepython.infra.JsonStore;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Runs pip download for the configured target platform, then writes manifest + SHA256SUMS. */
public class BuildService {

    /** @return a BuildSummary describing the build outcome (wheels, cache hits, size, duration). */
    public plugin.swisskit.offlinepython.domain.BuildSummary build(
            Path projectDir, BuildConfig cfg, String pythonExecutable,
            Consumer<String> onLog, ProcessRunner runner) throws Exception {
        Path output = projectDir.resolve(cfg.getRepository().getOutput());
        Path wheelhouse = output.resolve(cfg.getRepository().getWheelDir());
        Files.createDirectories(wheelhouse);

        int preExisting = countWheels(wheelhouse);
        Path reqs = projectDir.resolve("requirements.txt");
        List<String> cmd = ProcessRunner.pipDownloadCommand(
                pythonExecutable,
                reqs.toString(),
                wheelhouse.toString(),
                cfg.getPython().getPlatform(),
                majorMinor(cfg.getPython().getVersion()),
                cfg.getPython().getImplementation(),
                cfg.getDownload().isOnlyBinary());
        onLog.accept("$ " + String.join(" ", cmd));
        long start = System.currentTimeMillis();
        int code = runner.run(cmd, onLog);
        long duration = System.currentTimeMillis() - start;
        if (code != 0) {
            return new plugin.swisskit.offlinepython.domain.BuildSummary(preExisting, preExisting, 0L, duration);
        }
        writeManifest(projectDir, cfg, output, wheelhouse);
        writeSha256Sums(output);
        return plugin.swisskit.offlinepython.domain.BuildSummary.compute(wheelhouse, preExisting, duration);
    }

    private int countWheels(Path wheelhouse) throws IOException {
        try (Stream<Path> files = Files.list(wheelhouse)) {
            return (int) files.filter(p -> p.getFileName().toString().endsWith(".whl")).count();
        }
    }

    void writeManifest(Path projectDir, BuildConfig cfg, Path output, Path wheelhouse) throws IOException {
        List<WheelEntry> wheels = new ArrayList<>();
        List<String> reqNames = new ArrayList<>();
        for (var d : plugin.swisskit.offlinepython.domain.RequirementsFile.parse(
                Files.readString(projectDir.resolve("requirements.txt")))) {
            reqNames.add(d.toString());
        }

        try (Stream<Path> files = Files.list(wheelhouse)) {
            List<Path> sorted = files.sorted().toList();
            for (Path f : sorted) {
                if (!f.toString().endsWith(".whl")) continue;
                String name = wheelNamePart(f.getFileName().toString());
                String normName = DependencySpec.normalizeName(name);
                boolean required = reqNames.stream()
                        .anyMatch(r -> DependencySpec.normalizeName(r).startsWith(normName));
                wheels.add(new WheelEntry(
                        name, "", output.relativize(f).toString().replace('\\', '/'),
                        HashUtil.sha256Hex(f), Files.size(f), required));
            }
        }

        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.getPython().setVersion(cfg.getPython().getVersion());
        m.getPython().setPlatform(cfg.getPython().getPlatform());
        m.setBuiltAt(java.time.OffsetDateTime.now().toString());
        m.setBuiltOn(System.getProperty("user.name"));
        m.setToolVersion("1.0.0");
        m.getWheels().addAll(wheels);
        m.getRequirements().addAll(reqNames);
        JsonStore.save(m, output.resolve("manifest.json"));
    }

    void writeSha256Sums(Path output) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(output)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".whl")
                              || p.getFileName().toString().toLowerCase().contains("python"))
                    .sorted().toList();
            for (Path f : files) {
                sb.append(HashUtil.sha256Hex(f)).append("  ")
                  .append(output.relativize(f).toString().replace('\\', '/'))
                  .append(System.lineSeparator());
            }
        }
        Files.writeString(output.resolve("SHA256SUMS"), sb.toString());
    }

    /** "numpy-1.26.4-cp312-...whl" -> "numpy". */
    static String wheelNamePart(String fileName) {
        int dash = fileName.indexOf('-');
        return dash < 0 ? fileName : fileName.substring(0, dash);
    }

    static String majorMinor(String version) {
        String[] p = version.split("\\.");
        return p.length >= 2 ? p[0] + "." + p[1] : version;
    }
}
