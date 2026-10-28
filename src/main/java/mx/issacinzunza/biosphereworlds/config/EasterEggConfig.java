package mx.issacinzunza.biosphereworlds.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** One runtime switch shared by all Easter eggs. Details remain internal. */
public final class EasterEggConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("Easter Egg");
        ENABLED = builder.comment("Enable Easter eggs in Biospheres and Solar Systems.").define("enabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private EasterEggConfig() { }
}
