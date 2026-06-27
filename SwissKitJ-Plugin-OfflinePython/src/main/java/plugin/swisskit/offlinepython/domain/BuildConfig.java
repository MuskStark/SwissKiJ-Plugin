package plugin.swisskit.offlinepython.domain;

import lombok.Data;

public @Data class BuildConfig {

    private Python python = new Python();
    private Repository repository = new Repository();
    private Download download = new Download();
    private Pkg pkg = new Pkg();

    public static BuildConfig defaults() {
        BuildConfig c = new BuildConfig();
        c.python.version = "3.12.10";
        c.python.platform = "win_amd64";
        c.python.implementation = "cp";
        c.python.installer = true;
        c.repository.output = "output";
        c.repository.wheelDir = "wheelhouse";
        c.repository.cache = true;
        c.download.mirror = "official";
        c.download.upgradePip = true;
        c.download.recursive = true;
        c.download.onlyBinary = true;
        c.pkg.zip = true;
        c.pkg.sha256 = true;
        c.pkg.readme = true;
        return c;
    }

    @Data public static class Python {
        private String version;
        private String platform;
        private String implementation;
        private boolean installer;
        private String executable; // null = auto-detect
    }

    @Data public static class Repository {
        private String output;
        private String wheelDir;
        private boolean cache;
    }

    @Data public static class Download {
        private String mirror;
        private boolean upgradePip;
        private boolean recursive;
        private boolean onlyBinary;
    }

    @Data public static class Pkg {
        private boolean zip;
        private boolean sha256;
        private boolean readme;
    }
}
