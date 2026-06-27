package plugin.swisskit.offlinepython.infra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Gson-backed load/save of JSON model objects. */
public final class JsonStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonStore() {}

    public static <T> T load(Path file, Class<T> type) throws IOException {
        return GSON.fromJson(Files.readString(file), type);
    }

    public static <T> void save(T obj, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(obj));
    }
}
