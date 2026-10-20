package mx.issacinzunza.biosphereworlds.config;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarSettings;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Defaults for NEW Solar worlds. Their snapshot is persisted in the generator codec. */
public final class SolarConfig {
    public static final SphereConfig.GeneralOptions GENERAL;
    public static final SphereConfig.SolarOptions OPTIONS;
    public static final ModConfigSpec SPEC;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Independent Solar settings. Automatic bridges, classic grid spacing and hemispheres are disabled. Existing worlds retain their snapshot.").push("Settings");
        GENERAL = new SphereConfig.GeneralOptions(builder);
        builder.pop();
        builder.push("SolarOrbits");
        OPTIONS = new SphereConfig.SolarOptions(builder);
        builder.pop();
        SPEC = builder.build();
    }
    private SolarConfig() { }

    public static SolarSettings snapshot() {
        if (GENERAL.MIN_RADIUS.get().intValue() < 1
                || GENERAL.MIN_RADIUS.get().intValue() > GENERAL.RADIUS.get().intValue()) {
            throw new IllegalArgumentException("Solar Systems requires Settings.min_radius <= Settings.radius, both positive");
        }
        boolean cold = OPTIONS.SOLAR_CLIMATE_DIRECTION.get() == 1;
        var general = new SolarSettings.General(GENERAL.CENTER_Y.get().intValue(), List.copyOf(GENERAL.SPHERE_BLOCK.get()), GENERAL.BEDROCK_MODE.get(),
                new SolarSettings.Satellites(GENERAL.SATELLITES_MIN_SIZE_PERCENT.get(), GENERAL.SATELLITES_MAX_SIZE_PERCENT.get(), GENERAL.SATELLITES_MIN_GAP_PERCENT.get(),
                        GENERAL.SATELLITES_MAX_GAP_PERCENT.get(), List.copyOf(GENERAL.SATELLITES_INCLUDED_ORES.get()), List.copyOf(GENERAL.SATELLITES_EXCLUDED_ORES.get()), GENERAL.SATELLITES_MOD_ORE_WEIGHT.get(), GENERAL.SATELLITES_MOD_ORE_DENSITY_PERMILLE.get()), GENERAL.CLOSE_OPEN_TOP.get());
        int region = SolarLayout.requiredSystemRadius(OPTIONS.SOLAR_PLANET_COUNT.get(), GENERAL.RADIUS.get().intValue(), 180, OPTIONS.SOLAR_PLANET_GAP.get(),
                GENERAL.SATELLITES_MAX_GAP_PERCENT.get(), GENERAL.SATELLITES_MAX_SIZE_PERCENT.get(), GENERAL.SATELLITES_MIN_GAP_BLOCKS.get());
        var defaults = SolarSettings.defaults().guardians();
        var guardians = new SolarSettings.Guardians(OPTIONS.SOLAR_GUARDIANS_ENABLED.get(), defaults.ghasts(), defaults.phantoms(),
                defaults.radius(), defaults.ghastMax(), defaults.phantomMax(), defaults.interval());
        return new SolarSettings(new SolarLayout.Params(region, OPTIONS.SOLAR_SYSTEM_GAP.get(), OPTIONS.SOLAR_PLANET_COUNT.get(),
                GENERAL.MIN_RADIUS.get().intValue(), GENERAL.RADIUS.get().intValue(), 180, OPTIONS.SOLAR_PLANET_GAP.get(), OPTIONS.SOLAR_PLANET_GAP.get(), cold, 30, 40, 30,
                GENERAL.SATELLITES_ENABLED.get() ? GENERAL.SATELLITES_MAX_COUNT.get() : 0, GENERAL.SATELLITES_MIN_ELEVATION_DEGREES.get(), GENERAL.SATELLITES_MAX_ELEVATION_DEGREES.get(), GENERAL.SATELLITES_MIN_GAP_BLOCKS.get()),
                true, cold ? SolarLayout.Climate.COLD : SolarLayout.Climate.HOT, guardians,
                new SolarSettings.Environment(GENERAL.EDGE_OCEAN_ENABLED.get(), GENERAL.EDGE_OCEAN_WIDTH.get(), GENERAL.EDGE_OCEAN_DEPTH.get(), GENERAL.BEDROCK_MODE.get() == SphereConfig.BedrockMode.SHELL), general, 5);
    }
}
