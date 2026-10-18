package mx.issacinzunza.biosphereworlds.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
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
        RADIUS = BUILDER.comment("Max radius of the spheres").define("radius", 128);
        MIN_RADIUS = BUILDER.comment("Min radius of the spheres (same as radius for fixed size)").define("min_radius", 128);
        SPACING = BUILDER.comment("Spacing between the spheres").define("spacing", 500);
        CENTER_Y = BUILDER.comment("Center Y level for sphere generation").define("center_y", 62);
        SPHERE_BLOCK = BUILDER.comment("Blocks used for the spheres, randomly assigned per sphere").defineListAllowEmpty("sphere_block", java.util.List.of("minecraft:glass"), SphereConfig::validateItemName);
        ONLY_UPPER_HEMISPHERE = BUILDER.comment("Generate only the upper hemisphere of the spheres").define("only_upper_hemisphere", false);
        CLOSE_OPEN_TOP = BUILDER.comment("Close spheres cut by the build limit with a flat cap at the top buildable Y, using the sphere shell block").define("close_open_top", false);
        BEDROCK_MODE = BUILDER.comment("Bedrock at the world floor. floor: keep the bedrock_floor surface rule at the bottom of the world. none: no bedrock at the bottom, the floor is deepslate instead (the Create Diesel Generators pumpjack needs bedrock, so it will not work with this value). shell: floor plus a bedrock lining of 3 blocks on the inside of the glass shell in the lower half of each sphere. With only_upper_hemisphere enabled, shell behaves exactly like floor.").defineEnum("bedrock_mode", BedrockMode.FLOOR);
        GENERATE_BRIDGE = BUILDER.comment("Generate bridges between spheres").define("generate_bridge", true);
        BRIDGE_BLOCK = BUILDER.comment("Block used for the bridges").define("bridge_block", "minecraft:oak_planks", SphereConfig::validateItemName);
        BRIDGE_RADIUS = BUILDER.comment("Radius of the bridges").define("bridge_radius", 1);
        RESTRICT_STRUCTURES_TO_SPHERES = BUILDER.comment("Restrict structure starts to the inside of spheres").define("restrict_structures_to_spheres", true);
        STRUCTURE_EDGE_MARGIN = BUILDER.comment("Minimum distance between a structure start and a sphere edge").defineInRange("structure_edge_margin", 16, 0, 128);
        DEBUG_BIOME_LOOKUPS = BUILDER.comment("Log outside-sphere biome lookup routes at DEBUG level").define("debug_biome_lookups", false);
        EDGE_OCEAN_ENABLED = BUILDER.comment("Flood the band next to the sphere shell with ocean: the terrain is clamped down to a flat floor so water reaches the glass").define("edge_ocean_enabled", false);
        EDGE_OCEAN_WIDTH = BUILDER.comment("Width of the ocean band, as a percentage of the sphere radius").defineInRange("edge_ocean_width", 20, 0, 100);
        EDGE_OCEAN_DEPTH = BUILDER.comment("Depth of the ocean floor below sea level (63), in blocks").defineInRange("edge_ocean_depth", 20, 0, 100);
        SATELLITES_ENABLED = BUILDER.comment("Generate small satellite spheres above each main sphere. Only affects newly generated worlds.").define("satellites_enabled", true);
        SATELLITES_MAX_COUNT = BUILDER.comment("Maximum satellites per main sphere (0 disables them even if satellites_enabled is true)").defineInRange("satellites_max_count", 3, 0, 64);
        SATELLITES_MIN_SIZE_PERCENT = BUILDER.comment("Minimum satellite radius, as a percentage of the main sphere radius (default 8)").defineInRange("satellites_min_size_percent", 8, 1, 100);
        SATELLITES_MAX_SIZE_PERCENT = BUILDER.comment("Maximum satellite radius, as a percentage of the main sphere radius (default 12)").defineInRange("satellites_max_size_percent", 12, 1, 100);
        SATELLITES_MIN_GAP_PERCENT = BUILDER.comment("Minimum gap between the satellite and main sphere surfaces, as a percentage of the main radius").defineInRange("satellites_min_gap_percent", 25, 0, 1000);
        SATELLITES_MAX_GAP_PERCENT = BUILDER.comment("Maximum gap between the satellite and main sphere surfaces, as a percentage of the main radius").defineInRange("satellites_max_gap_percent", 75, 0, 1000);
        SATELLITES_MIN_GAP_BLOCKS = BUILDER.comment("Absolute minimum gap, in blocks, with the main sphere; also the safety margin from the cell border and other satellites").defineInRange("satellites_min_gap_blocks", 16, 0, 1024);
        SATELLITES_MIN_ELEVATION_DEGREES = BUILDER.comment("Minimum elevation angle of the satellites above the main sphere center, in degrees").defineInRange("satellites_min_elevation_degrees", 30, 0, 90);
        SATELLITES_MAX_ELEVATION_DEGREES = BUILDER.comment("Maximum elevation angle of the satellites above the main sphere center, in degrees").defineInRange("satellites_max_elevation_degrees", 55, 0, 90);
        SATELLITES_EXCLUDED_ORES = BUILDER.comment("Ore block ids or #tags (for example #c:ores_in_ground/deepslate) that must never be chosen for a satellite core. Invalid entries are ignored with a warning. Changes apply when the world is reopened or with /reload.").defineListAllowEmpty("satellites_excluded_ores", java.util.List.of(
                "minecraft:ancient_debris",
                "minecraft:nether_quartz_ore",
                "minecraft:nether_gold_ore",
                "#c:ores_in_ground/deepslate"), SphereConfig::validateOreListEntry);
        SATELLITES_INCLUDED_ORES = BUILDER.comment("Ore block ids or #tags to add to the satellite core candidates even when they are not in #c:ores_in_ground/stone. Invalid entries are ignored with a warning. Changes apply when the world is reopened or with /reload.").defineListAllowEmpty("satellites_included_ores", java.util.List.of(), SphereConfig::validateOreListEntry);
        SATELLITES_MOD_ORE_WEIGHT = BUILDER.comment("Selection weight for ores added by other mods (vanilla ores keep their own weights). Changes apply when the world is reopened or with /reload.").defineInRange("satellites_mod_ore_weight", 3, 0, 1000);
        SATELLITES_MOD_ORE_DENSITY_PERMILLE = BUILDER.comment("Ore blocks per 1000 core blocks for ores added by other mods, in permille (20 = 2%). Changes apply when the world is reopened or with /reload.").defineInRange("satellites_mod_ore_density_permille", 20, 0, 1000);
        SPEC = BUILDER.build();
    }

    /** Valores de {@code bedrock_mode}. */
    public enum BedrockMode {
        FLOOR,
        NONE,
        SHELL
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
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

    public static void setup(ModContainer modContainer)
    {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "BiosphereWorlds.toml");
    }
}
