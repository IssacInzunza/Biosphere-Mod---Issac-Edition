package mx.issacinzunza.biosphereworlds.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class SphereConfig {
    private static final ModConfigSpec SPEC;
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<Number> RADIUS;
    public static final ModConfigSpec.ConfigValue<Number> MIN_RADIUS;
    public static final ModConfigSpec.ConfigValue<Number> SPACING;
    public static final ModConfigSpec.ConfigValue<Number> CENTER_Y;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SPHERE_BLOCK;
    public static final ModConfigSpec.ConfigValue<Boolean> ONLY_UPPER_HEMISPHERE;
    public static final ModConfigSpec.BooleanValue CLOSE_OPEN_TOP;
    public static final ModConfigSpec.EnumValue<BedrockMode> BEDROCK_MODE;
    public static final ModConfigSpec.ConfigValue<Boolean> GENERATE_BRIDGE;
    public static final ModConfigSpec.ConfigValue<String> BRIDGE_BLOCK;
    public static final ModConfigSpec.ConfigValue<Number> BRIDGE_RADIUS;
    public static final ModConfigSpec.ConfigValue<Boolean> RESTRICT_STRUCTURES_TO_SPHERES;
    public static final ModConfigSpec.IntValue STRUCTURE_EDGE_MARGIN;
    public static final ModConfigSpec.BooleanValue DEBUG_BIOME_LOOKUPS;
    public static final ModConfigSpec.BooleanValue EDGE_OCEAN_ENABLED;
    public static final ModConfigSpec.IntValue EDGE_OCEAN_WIDTH;
    public static final ModConfigSpec.IntValue EDGE_OCEAN_DEPTH;
    public static final ModConfigSpec.BooleanValue SATELLITES_ENABLED;
    public static final ModConfigSpec.IntValue SATELLITES_MAX_COUNT;
    public static final ModConfigSpec.IntValue SATELLITES_MIN_SIZE_PERCENT;
    public static final ModConfigSpec.IntValue SATELLITES_MAX_SIZE_PERCENT;
    public static final ModConfigSpec.IntValue SATELLITES_MIN_GAP_PERCENT;
    public static final ModConfigSpec.IntValue SATELLITES_MAX_GAP_PERCENT;
    public static final ModConfigSpec.IntValue SATELLITES_MIN_GAP_BLOCKS;
    public static final ModConfigSpec.IntValue SATELLITES_MIN_ELEVATION_DEGREES;
    public static final ModConfigSpec.IntValue SATELLITES_MAX_ELEVATION_DEGREES;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SATELLITES_EXCLUDED_ORES;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SATELLITES_INCLUDED_ORES;
    public static final ModConfigSpec.IntValue SATELLITES_MOD_ORE_WEIGHT;
    public static final ModConfigSpec.IntValue SATELLITES_MOD_ORE_DENSITY_PERMILLE;

    static {
        BUILDER.push("Settings");
        var general = new GeneralOptions(BUILDER);
        RADIUS = general.RADIUS;
        MIN_RADIUS = general.MIN_RADIUS;
        SPACING = BUILDER.comment("Spacing between the spheres (positive integer)").define("spacing", 500,
                value -> isIntegerInRange(value, 1, Integer.MAX_VALUE));
        CENTER_Y = general.CENTER_Y;
        SPHERE_BLOCK = general.SPHERE_BLOCK;
        ONLY_UPPER_HEMISPHERE = BUILDER.comment("Generate only the upper hemisphere of the spheres").define("only_upper_hemisphere", false);
        CLOSE_OPEN_TOP = general.CLOSE_OPEN_TOP;
        BEDROCK_MODE = general.BEDROCK_MODE;
        GENERATE_BRIDGE = BUILDER.comment("Generate bridges between spheres").define("generate_bridge", true);
        BRIDGE_BLOCK = BUILDER.comment("Block used for the bridges").define("bridge_block", "minecraft:oak_planks", SphereConfig::validateBlockName);
        BRIDGE_RADIUS = BUILDER.comment("Radius of the bridges (0..46340)").define("bridge_radius", 1,
                value -> isIntegerInRange(value, 0, 46340));
        RESTRICT_STRUCTURES_TO_SPHERES = BUILDER.comment("Restrict structure starts to the inside of spheres").define("restrict_structures_to_spheres", true);
        STRUCTURE_EDGE_MARGIN = BUILDER.comment("Minimum distance between a structure start and a sphere edge").defineInRange("structure_edge_margin", 16, 0, 128);
        DEBUG_BIOME_LOOKUPS = BUILDER.comment("Log outside-sphere biome lookup routes at DEBUG level").define("debug_biome_lookups", false);
        EDGE_OCEAN_ENABLED = general.EDGE_OCEAN_ENABLED;
        EDGE_OCEAN_WIDTH = general.EDGE_OCEAN_WIDTH;
        EDGE_OCEAN_DEPTH = general.EDGE_OCEAN_DEPTH;
        SATELLITES_ENABLED = general.SATELLITES_ENABLED;
        SATELLITES_MAX_COUNT = general.SATELLITES_MAX_COUNT;
        SATELLITES_MIN_SIZE_PERCENT = general.SATELLITES_MIN_SIZE_PERCENT;
        SATELLITES_MAX_SIZE_PERCENT = general.SATELLITES_MAX_SIZE_PERCENT;
        SATELLITES_MIN_GAP_PERCENT = general.SATELLITES_MIN_GAP_PERCENT;
        SATELLITES_MAX_GAP_PERCENT = general.SATELLITES_MAX_GAP_PERCENT;
        SATELLITES_MIN_GAP_BLOCKS = general.SATELLITES_MIN_GAP_BLOCKS;
        SATELLITES_MIN_ELEVATION_DEGREES = general.SATELLITES_MIN_ELEVATION_DEGREES;
        SATELLITES_MAX_ELEVATION_DEGREES = general.SATELLITES_MAX_ELEVATION_DEGREES;
        SATELLITES_EXCLUDED_ORES = general.SATELLITES_EXCLUDED_ORES;
        SATELLITES_INCLUDED_ORES = general.SATELLITES_INCLUDED_ORES;
        SATELLITES_MOD_ORE_WEIGHT = general.SATELLITES_MOD_ORE_WEIGHT;
        SATELLITES_MOD_ORE_DENSITY_PERMILLE = general.SATELLITES_MOD_ORE_DENSITY_PERMILLE;
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /** One definition of the applicable options, instantiated independently for each config file. */
    public static final class GeneralOptions {
        public final ModConfigSpec.ConfigValue<Number> RADIUS;
        public final ModConfigSpec.ConfigValue<Number> MIN_RADIUS;
        public final ModConfigSpec.ConfigValue<Number> CENTER_Y;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SPHERE_BLOCK;
        public final ModConfigSpec.BooleanValue CLOSE_OPEN_TOP;
        public final ModConfigSpec.EnumValue<BedrockMode> BEDROCK_MODE;
        public final ModConfigSpec.BooleanValue EDGE_OCEAN_ENABLED;
        public final ModConfigSpec.IntValue EDGE_OCEAN_WIDTH;
        public final ModConfigSpec.IntValue EDGE_OCEAN_DEPTH;
        public final ModConfigSpec.BooleanValue SATELLITES_ENABLED;
        public final ModConfigSpec.IntValue SATELLITES_MAX_COUNT;
        public final ModConfigSpec.IntValue SATELLITES_MIN_SIZE_PERCENT;
        public final ModConfigSpec.IntValue SATELLITES_MAX_SIZE_PERCENT;
        public final ModConfigSpec.IntValue SATELLITES_MIN_GAP_PERCENT;
        public final ModConfigSpec.IntValue SATELLITES_MAX_GAP_PERCENT;
        public final ModConfigSpec.IntValue SATELLITES_MIN_GAP_BLOCKS;
        public final ModConfigSpec.IntValue SATELLITES_MIN_ELEVATION_DEGREES;
        public final ModConfigSpec.IntValue SATELLITES_MAX_ELEVATION_DEGREES;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SATELLITES_EXCLUDED_ORES;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SATELLITES_INCLUDED_ORES;
        public final ModConfigSpec.IntValue SATELLITES_MOD_ORE_WEIGHT;
        public final ModConfigSpec.IntValue SATELLITES_MOD_ORE_DENSITY_PERMILLE;
        public GeneralOptions(ModConfigSpec.Builder builder) {
            RADIUS = builder.comment("Max radius of the spheres (1..46340; integer-square safety limit)").define("radius", 128,
                    value -> isIntegerInRange(value, 1, 46340));
            MIN_RADIUS = builder.comment("Min radius of the spheres (1..radius; same as radius for fixed size)").define("min_radius", 128,
                    value -> isIntegerInRange(value, 1, 46340));
            CENTER_Y = builder.comment("Center Y level for sphere generation (integer)").define("center_y", 62,
                    value -> isIntegerInRange(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
            SPHERE_BLOCK = builder.comment("Blocks used for the spheres, randomly assigned per sphere").defineListAllowEmpty("sphere_block", java.util.List.of("minecraft:glass"), SphereConfig::validateBlockName);
            CLOSE_OPEN_TOP = builder.comment("Close spheres cut by the build limit with a flat cap at the top buildable Y, using the sphere shell block").define("close_open_top", false);
            BEDROCK_MODE = builder.comment("Bedrock at the world floor. floor: keep the bedrock_floor surface rule at the bottom of the world. none: no bedrock at the bottom, the floor is deepslate instead (the Create Diesel Generators pumpjack needs bedrock, so it will not work with this value). shell: floor plus a bedrock lining of 3 blocks on the inside of the glass shell in the lower half of each sphere. With only_upper_hemisphere enabled, shell behaves exactly like floor.").defineEnum("bedrock_mode", BedrockMode.FLOOR);
            EDGE_OCEAN_ENABLED = builder.comment("Flood the band next to the sphere shell with ocean: the terrain is clamped down to a flat floor so water reaches the glass").define("edge_ocean_enabled", false);
            EDGE_OCEAN_WIDTH = builder.comment("Width of the ocean band, as a percentage of the sphere radius").defineInRange("edge_ocean_width", 20, 0, 100);
            EDGE_OCEAN_DEPTH = builder.comment("Depth of the ocean floor below sea level (63), in blocks").defineInRange("edge_ocean_depth", 20, 0, 100);
            SATELLITES_ENABLED = builder.comment("Generate small satellite spheres above each main sphere. Only affects newly generated worlds.").define("satellites_enabled", true);
            SATELLITES_MAX_COUNT = builder.comment("Maximum satellites per main sphere (0 disables them even if satellites_enabled is true)").defineInRange("satellites_max_count", 3, 0, 64);
            SATELLITES_MIN_SIZE_PERCENT = builder.comment("Minimum satellite radius, as a percentage of the main sphere radius (default 8)").defineInRange("satellites_min_size_percent", 8, 1, 100);
            SATELLITES_MAX_SIZE_PERCENT = builder.comment("Maximum satellite radius, as a percentage of the main sphere radius (default 12)").defineInRange("satellites_max_size_percent", 12, 1, 100);
            SATELLITES_MIN_GAP_PERCENT = builder.comment("Minimum gap between the satellite and main sphere surfaces, as a percentage of the main radius").defineInRange("satellites_min_gap_percent", 25, 0, 1000);
            SATELLITES_MAX_GAP_PERCENT = builder.comment("Maximum gap between the satellite and main sphere surfaces, as a percentage of the main radius").defineInRange("satellites_max_gap_percent", 75, 0, 1000);
            SATELLITES_MIN_GAP_BLOCKS = builder.comment("Absolute minimum gap, in blocks, with the main sphere; also the safety margin from the cell border and other satellites").defineInRange("satellites_min_gap_blocks", 16, 0, 1024);
            SATELLITES_MIN_ELEVATION_DEGREES = builder.comment("Minimum elevation angle of the satellites above the main sphere center, in degrees").defineInRange("satellites_min_elevation_degrees", 30, 0, 90);
            SATELLITES_MAX_ELEVATION_DEGREES = builder.comment("Maximum elevation angle of the satellites above the main sphere center, in degrees").defineInRange("satellites_max_elevation_degrees", 55, 0, 90);
            SATELLITES_EXCLUDED_ORES = builder.comment("Ore block ids or #tags (for example #c:ores_in_ground/deepslate) that must never be chosen for a satellite core. Ignored entries are logged. Classic Biospheres rebuilds on tag reload or reopening. Solar saves these settings per world and resolves its catalog on opening, not /reload; TOML edits apply to new Solar worlds only.").defineListAllowEmpty("satellites_excluded_ores", java.util.List.of(
                    "minecraft:ancient_debris",
                    "minecraft:nether_quartz_ore",
                    "minecraft:nether_gold_ore",
                    "#c:ores_in_ground/deepslate"), SphereConfig::validateOreListEntry);
            SATELLITES_INCLUDED_ORES = builder.comment("Ore block ids or #tags to add to the satellite core candidates even when they are not in #c:ores_in_ground/stone. Ignored entries are logged. Classic Biospheres rebuilds on tag reload or reopening. Solar saves these settings per world and resolves its catalog on opening, not /reload; TOML edits apply to new Solar worlds only.").defineListAllowEmpty("satellites_included_ores", java.util.List.of(), SphereConfig::validateOreListEntry);
            SATELLITES_MOD_ORE_WEIGHT = builder.comment("Selection weight for ores added by other mods (vanilla ores keep their own weights). Classic Biospheres rebuilds on tag reload or reopening. Solar saves this value per world; TOML edits apply to new Solar worlds only, not reopening or /reload.").defineInRange("satellites_mod_ore_weight", 3, 0, 1000);
            SATELLITES_MOD_ORE_DENSITY_PERMILLE = builder.comment("Ore blocks per 1000 core blocks for ores added by other mods, in permille (20 = 2%). Classic Biospheres rebuilds on tag reload or reopening. Solar saves this value per world; TOML edits apply to new Solar worlds only, not reopening or /reload.").defineInRange("satellites_mod_ore_density_permille", 20, 0, 1000);
        }
    }

    public static final class SolarOptions {
        public final ModConfigSpec.IntValue SOLAR_PLANET_COUNT;
        public final ModConfigSpec.IntValue SOLAR_PLANET_GAP;
        public final ModConfigSpec.IntValue SOLAR_SYSTEM_GAP;
        public final ModConfigSpec.IntValue SOLAR_CLIMATE_DIRECTION;
        public final ModConfigSpec.BooleanValue SOLAR_GUARDIANS_ENABLED;
        public final ModConfigSpec.BooleanValue SOLAR_GUIDE_ENABLED;

        public SolarOptions(ModConfigSpec.Builder builder) {
            SOLAR_PLANET_COUNT = builder.translation("biosphereworlds.configuration.solar_planet_count")
                    .comment("Exact number of orbital planets, excluding the central Stronghold sphere and satellites. New Solar worlds only.")
                    .defineInRange("planet_count", 8, 1, 16);
            SOLAR_PLANET_GAP = builder.translation("biosphereworlds.configuration.solar_planet_gap")
                    .comment("Minimum empty gap between planet surfaces, in blocks, including the central sphere. New Solar worlds only.")
                    .defineInRange("planet_gap", 400, 0, 4000);
            SOLAR_SYSTEM_GAP = builder.translation("biosphereworlds.configuration.solar_system_gap")
                    .comment("Minimum empty gap between system envelopes, in blocks. System radius is calculated automatically. New Solar worlds only.")
                    .defineInRange("system_gap", 1500, 0, 16000);
            SOLAR_CLIMATE_DIRECTION = builder.translation("biosphereworlds.configuration.solar_climate_direction")
                    .comment("0: magma/lava center, warm inner planets, temperate middle, cold exterior. 1: ice center, cold inner planets, temperate middle, warm exterior. New Solar worlds only.")
                    .defineInRange("climate_direction", 0, 0, 1);
            SOLAR_GUARDIANS_ENABLED = builder.translation("biosphereworlds.configuration.solar_guardians_enabled")
                    .comment("Generate twelve surface-covering cages for ghast, phantom and blaze guardians. Eight guardians within 32 blocks of a player, 24 per sun; up to two every 8-12 seconds. Existing marked cages upgrade when enabled. Other mobs are unaffected. Saved with each new Solar world.")
                    .define("guardians_enabled", true);
            SOLAR_GUIDE_ENABLED = builder.translation("biosphereworlds.configuration.solar_guide_enabled")
                    .comment("Enable the violet guide star toward the current Solar system center. Runtime setting, including existing worlds. False on the server stops guide calculations and packets for everyone; false on a client disables its guide rendering and orientation calculations. Server changes may take up to 40 ticks to hide an already received guide.")
                    .define("solar_guide_enabled", true);
        }
    }

    /** Valores de {@code bedrock_mode}. */
    public enum BedrockMode {
        FLOOR,
        NONE,
        SHELL
    }

    private static boolean validateBlockName(final Object obj) {
        if (!(obj instanceof String name)) return false;
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    private static boolean isIntegerInRange(Object value, int min, int max) {
        if (!(value instanceof Number number)) return false;
        double numeric = number.doubleValue();
        return numeric >= min && numeric <= max && numeric == Math.rint(numeric);
    }

    /**
     * Acepta un id de bloque o un tag con prefijo {@code #}. No resuelve contra
     * registros ni tags: eso se hace al reconstruir el catalogo, cuando los datos
     * ya estan cargados.
     */
    private static boolean validateOreListEntry(final Object obj) {
        if (!(obj instanceof String entry)) {
            return false;
        }
        String id = entry.startsWith("#") ? entry.substring(1) : entry;
        return !id.isBlank() && ResourceLocation.tryParse(id) != null;
    }

    /** Preserve previous defaults without deleting old files or overwriting an existing destination. */
    public static void migrateConfigFiles(java.nio.file.Path configDirectory) {
        try {
            var folder = configDirectory.resolve("biosphereworlds");
            java.nio.file.Files.createDirectories(folder);
            var classic = configDirectory.resolve("BiosphereWorlds.toml");
            var solar = configDirectory.resolve("SolarSystems.toml");
            var classicTarget = folder.resolve("BiosphereWorlds.toml");
            var solarTarget = folder.resolve("SolarSystems.toml");
            String previous = java.nio.file.Files.exists(classic) ? java.nio.file.Files.readString(classic) : "";
            if (!java.nio.file.Files.exists(solarTarget)) {
                String content = previous.contains("[SolarOrbits]") ? previous
                        : java.nio.file.Files.exists(solar) ? java.nio.file.Files.readString(solar) : previous;
                if (!content.isEmpty()) java.nio.file.Files.writeString(solarTarget, content.replace("[SolarGeneral]", "[Settings]")
                        .replace("climate_direction = \"hot_to_cold\"", "climate_direction = 0")
                        .replace("climate_direction = \"cold_to_hot\"", "climate_direction = 1"));
            }
            if (!java.nio.file.Files.exists(classicTarget) && java.nio.file.Files.exists(classic)) java.nio.file.Files.copy(classic, classicTarget);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot prepare biosphereworlds configuration folder", e);
        }
    }

    public static void setup(ModContainer modContainer)
    {
        migrateConfigFiles(FMLPaths.CONFIGDIR.get());
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "biosphereworlds/BiosphereWorlds.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, SolarConfig.SPEC, "biosphereworlds/SolarSystems.toml");
    }
}
