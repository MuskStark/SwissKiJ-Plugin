package plugin.swisskit.offlinepython.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Manifest {
    private int schemaVersion = 1;
    private Python python = new Python();
    private String builtAt;
    private String builtOn;
    private String toolVersion;
    private List<WheelEntry> wheels = new ArrayList<>();
    private List<String> requirements = new ArrayList<>();

    @Data
    public static class Python {
        private String version;
        private String platform;
        private String installer;       // relative path
        private String installerSha256;
    }
}
