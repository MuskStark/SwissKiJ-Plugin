package plugin.swisskit.offlinepython.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import plugin.swisskit.offlinepython.infra.ProcessRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency lookups for the DepsPanel: latest version via `pip index versions`,
 * and best-effort wheel size via the PyPI JSON API. Pure parsers are unit-tested;
 * the live calls are thin wrappers.
 */
public final class DepsService {

    private static final Pattern FIRST_VERSION = Pattern.compile("\\(([0-9][^)]*)\\)");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private DepsService() {}

    /** Run `pip index versions <pkg>`, return the latest version, or empty. */
    public Optional<String> latestVersion(String pkg, String pythonExe) {
        String out = ProcessRunner.captureQuiet(pythonExe, "-m", "pip", "index", "versions", pkg);
        return parseLatestVersion(out);
    }

    /** Best-effort wheel size (bytes) for pkg@versionSpec on platform; 0 if unknown. */
    public long fetchSizeBytes(String pkg, String versionSpec, String platform) {
        String ver = versionSpec == null ? "" : versionSpec.replaceAll("[<>=!~]", "").trim();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://pypi.org/pypi/" + pkg + "/json"))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return parsePyPIWheelSize(resp.body(), platform, ver);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** `pip index versions numpy` → "numpy (1.26.4)\n..." → "1.26.4". */
    public static Optional<String> parseLatestVersion(String out) {
        if (out == null || out.isBlank()) return Optional.empty();
        Matcher m = FIRST_VERSION.matcher(out);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    /** Extract the size of the wheel matching platform (and version if given) from PyPI JSON; 0 if none. */
    public static long parsePyPIWheelSize(String json, String platform, String version) {
        if (json == null || json.isBlank()) return 0L;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Iterable<JsonElement> urls = root.has("urls")
                    ? root.getAsJsonArray("urls")
                    : () -> root.entrySet().stream().map(java.util.Map.Entry::getValue).iterator();
            long fallback = 0L;
            for (JsonElement e : urls) {
                JsonObject o = e.getAsJsonObject();
                String fn = o.get("filename").getAsString();
                long size = o.get("size").getAsLong();
                if (!fn.endsWith(".whl")) continue;
                if (version != null && !version.isBlank() && !fn.contains(version)) continue;
                if (platform != null && !platform.isBlank() && fn.contains(platform)) return size;
                if (fallback == 0L) fallback = size;
            }
            return fallback;
        } catch (Exception ex) {
            return 0L;
        }
    }
}
