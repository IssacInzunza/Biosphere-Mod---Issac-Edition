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
    public static final ModConfigSpec.ConfigValue<Boolean> GENERATE_BRIDGE;
    public static final ModConfigSpec.ConfigValue<String> BRIDGE_BLOCK;
    public static final ModConfigSpec.ConfigValue<Number> BRIDGE_RADIUS;
    public static final ModConfigSpec.ConfigValue<Boolean> RESTRICT_STRUCTURES_TO_SPHERES;
    public static final ModConfigSpec.IntValue STRUCTURE_EDGE_MARGIN;
    public static final ModConfigSpec.BooleanValue DEBUG_BIOME_LOOKUPS;
    public static final ModConfigSpec.BooleanValue EDGE_OCEAN_ENABLED;
    public static final ModConfigSpec.IntValue EDGE_OCEAN_WIDTH;
    public static final ModConfigSpec.IntValue EDGE_OCEAN_DEPTH;

    static {
        BUILDER.push("Settings");
        RADIUS = BUILDER.comment("Max radius of the spheres").define("radius", 128);
        MIN_RADIUS = BUILDER.comment("Min radius of the spheres (same as radius for fixed size)").define("min_radius", 128);
        SPACING = BUILDER.comment("Spacing between the spheres").define("spacing", 500);
        CENTER_Y = BUILDER.comment("Center Y level for sphere generation").define("center_y", 62);
        SPHERE_BLOCK = BUILDER.comment("Blocks used for the spheres, randomly assigned per sphere").defineListAllowEmpty("sphere_block", java.util.List.of("minecraft:glass"), SphereConfig::validateItemName);
        ONLY_UPPER_HEMISPHERE = BUILDER.comment("Generate only the upper hemisphere of the spheres").define("only_upper_hemisphere", false);
        CLOSE_OPEN_TOP = BUILDER.comment("Close spheres extending above Y=320 with a flat shell-material cap at Y=319. Removes the natural aircraft exit; opaque shells reduce interior light.").define("close_open_top", false);
        GENERATE_BRIDGE = BUILDER.comment("Generate bridges between spheres").define("generate_bridge", true);
        BRIDGE_BLOCK = BUILDER.comment("Block used for the bridges").define("bridge_block", "minecraft:oak_planks", SphereConfig::validateItemName);
        BRIDGE_RADIUS = BUILDER.comment("Radius of the bridges").define("bridge_radius", 1);
        RESTRICT_STRUCTURES_TO_SPHERES = BUILDER.comment("Restrict structure starts to the inside of spheres").define("restrict_structures_to_spheres", true);
        STRUCTURE_EDGE_MARGIN = BUILDER.comment("Minimum distance between a structure start and a sphere edge").defineInRange("structure_edge_margin", 16, 0, 128);
        DEBUG_BIOME_LOOKUPS = BUILDER.comment("Log outside-sphere biome lookup routes at DEBUG level").define("debug_biome_lookups", false);
        EDGE_OCEAN_ENABLED = BUILDER.comment("Flood the band next to the sphere shell with ocean: the terrain is clamped down to a flat floor so water reaches the glass").define("edge_ocean_enabled", false);
        EDGE_OCEAN_WIDTH = BUILDER.comment("Width of the ocean band, as a percentage of the sphere radius").defineInRange("edge_ocean_width", 20, 0, 100);
        EDGE_OCEAN_DEPTH = BUILDER.comment("Depth of the ocean floor below sea level (63), in blocks").defineInRange("edge_ocean_depth", 20, 0, 100);
        SPEC = BUILDER.build();
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    public static void setup(ModContainer modContainer)
    {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "BiosphereWorlds.toml");
    }
}
