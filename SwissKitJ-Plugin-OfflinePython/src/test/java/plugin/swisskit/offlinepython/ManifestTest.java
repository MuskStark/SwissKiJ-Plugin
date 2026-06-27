package plugin.swisskit.offlinepython;

import org.junit.jupiter.api.Test;
import plugin.swisskit.offlinepython.domain.Manifest;
import plugin.swisskit.offlinepython.domain.WheelEntry;
import plugin.swisskit.offlinepython.infra.JsonStore;

import static org.junit.jupiter.api.Assertions.*;

class ManifestTest {

    @Test
    void manifestTracksSchemaVersionAndWheels() {
        Manifest m = new Manifest();
        m.setSchemaVersion(1);
        m.setBuiltAt("2026-06-26T14:52:48");
        m.getWheels().add(new WheelEntry("numpy", "1.26.4",
            "wheelhouse/numpy-1.26.4-cp312-cp312-win_amd64.whl",
            "deadbeef", 19098624L, true));

        String json = new com.google.gson.GsonBuilder().create().toJson(m);
        Manifest back = JsonStore.fromJson(json, Manifest.class);

        assertEquals(1, back.getSchemaVersion());
        assertEquals(1, back.getWheels().size());
        assertEquals("numpy", back.getWheels().get(0).getName());
        assertTrue(back.getWheels().get(0).isRequired());
        assertTrue(json.contains("\"schemaVersion\""));
    }
}
