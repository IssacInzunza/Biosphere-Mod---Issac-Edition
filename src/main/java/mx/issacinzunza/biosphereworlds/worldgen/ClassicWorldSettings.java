package mx.issacinzunza.biosphereworlds.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Receta clasica persistida; no consulta TOML una vez vinculada al mundo. */
public record ClassicWorldSettings(SphereLayout.Params geometry, List<String> shellBlocks,
        SphereConfig.BedrockMode bedrockMode, boolean onlyUpperHemisphere, boolean closeOpenTop,
        Bridges bridges, Ocean ocean, Structures structures, List<SatelliteLayers.Mineral> minerals,
        boolean upperShellEnabled, boolean lowerShellEnabled) {
    public ClassicWorldSettings(SphereLayout.Params geometry, List<String> shellBlocks,
            SphereConfig.BedrockMode bedrockMode, boolean onlyUpperHemisphere, boolean closeOpenTop,
            Bridges bridges, Ocean ocean, Structures structures, List<SatelliteLayers.Mineral> minerals) {
        this(geometry, shellBlocks, bedrockMode, onlyUpperHemisphere, closeOpenTop,
                bridges, ocean, structures, minerals, true, true);
    }
    public record Bridges(boolean enabled, int radius, String block) {
        public static final Codec<Bridges> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("enabled").forGetter(Bridges::enabled),
                Codec.intRange(0, 46340).fieldOf("radius").forGetter(Bridges::radius),
                Codec.STRING.fieldOf("block").forGetter(Bridges::block)).apply(i, Bridges::new));
    }
    public record Ocean(boolean enabled, int width, int depth) {
        public static final Codec<Ocean> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("enabled").forGetter(Ocean::enabled),
                Codec.intRange(0, 100).fieldOf("width").forGetter(Ocean::width),
                Codec.intRange(0, 100).fieldOf("depth").forGetter(Ocean::depth)).apply(i, Ocean::new));
    }
    public record Structures(boolean restricted, int margin) {
        public static final Codec<Structures> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("restricted").forGetter(Structures::restricted),
                Codec.intRange(0, 128).fieldOf("margin").forGetter(Structures::margin)).apply(i, Structures::new));
    }
    private static final Codec<SphereLayout.Params> GEOMETRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("spacing").forGetter(SphereLayout.Params::spacing),
            Codec.INT.fieldOf("center_y").forGetter(SphereLayout.Params::centerY),
            Codec.intRange(1, 46340).fieldOf("min_radius").forGetter(SphereLayout.Params::minRadius),
            Codec.intRange(1, 46340).fieldOf("radius").forGetter(SphereLayout.Params::maxRadius),
            Codec.BOOL.fieldOf("satellites_enabled").forGetter(SphereLayout.Params::satellitesEnabled),
            Codec.intRange(0, 64).fieldOf("satellites_max_count").forGetter(SphereLayout.Params::satellitesMaxCount),
            Codec.intRange(1, 100).fieldOf("satellites_min_size_percent").forGetter(SphereLayout.Params::satellitesMinSizePercent),
            Codec.intRange(1, 100).fieldOf("satellites_max_size_percent").forGetter(SphereLayout.Params::satellitesMaxSizePercent),
            Codec.intRange(0, 1000).fieldOf("satellites_min_gap_percent").forGetter(SphereLayout.Params::satellitesMinGapPercent),
            Codec.intRange(0, 1000).fieldOf("satellites_max_gap_percent").forGetter(SphereLayout.Params::satellitesMaxGapPercent),
            Codec.intRange(0, 1024).fieldOf("satellites_min_gap_blocks").forGetter(SphereLayout.Params::satellitesMinGapBlocks),
            Codec.intRange(0, 90).fieldOf("satellites_min_elevation_degrees").forGetter(SphereLayout.Params::satellitesMinElevationDegrees),
            Codec.intRange(0, 90).fieldOf("satellites_max_elevation_degrees").forGetter(SphereLayout.Params::satellitesMaxElevationDegrees)
    ).apply(i, ClassicWorldSettings::decodeGeometry));
    private static final Codec<SatelliteLayers.Mineral> MINERAL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("block").forGetter(SatelliteLayers.Mineral::blockId),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("weight").forGetter(SatelliteLayers.Mineral::weight),
            Codec.intRange(0, 1000).fieldOf("density").forGetter(SatelliteLayers.Mineral::densityPermille)
    ).apply(i, SatelliteLayers.Mineral::new));
    public static final Codec<ClassicWorldSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            GEOMETRY_CODEC.fieldOf("geometry").forGetter(ClassicWorldSettings::geometry),
            Codec.STRING.listOf().fieldOf("shell_blocks").forGetter(ClassicWorldSettings::shellBlocks),
            Codec.STRING.xmap(SphereConfig.BedrockMode::valueOf, Enum::name).fieldOf("bedrock_mode").forGetter(ClassicWorldSettings::bedrockMode),
            Codec.BOOL.fieldOf("only_upper_hemisphere").forGetter(ClassicWorldSettings::onlyUpperHemisphere),
            Codec.BOOL.fieldOf("close_open_top").forGetter(ClassicWorldSettings::closeOpenTop),
            Bridges.CODEC.fieldOf("bridges").forGetter(ClassicWorldSettings::bridges),
            Ocean.CODEC.fieldOf("ocean").forGetter(ClassicWorldSettings::ocean),
            Structures.CODEC.fieldOf("structures").forGetter(ClassicWorldSettings::structures),
            MINERAL_CODEC.listOf().fieldOf("minerals").forGetter(ClassicWorldSettings::minerals),
            Codec.BOOL.optionalFieldOf("upper_shell_enabled", true).forGetter(ClassicWorldSettings::upperShellEnabled),
            Codec.BOOL.optionalFieldOf("lower_shell_enabled", true).forGetter(ClassicWorldSettings::lowerShellEnabled)
    ).apply(i, ClassicWorldSettings::new));

    public ClassicWorldSettings {
        shellBlocks = List.copyOf(shellBlocks);
        minerals = List.copyOf(minerals);
        SphereLayout.validateConfiguredRadii(geometry.minRadius(), geometry.maxRadius());
        if (geometry.satellitesEnabled() && geometry.satellitesMaxCount() > 0
                && !MineralCatalog.hasSelectableMineral(minerals)) {
            throw new IllegalArgumentException("Saved classic satellites require a positive-weight mineral");
        }
    }

    /** Rechaza pares invertidos guardados antes de que Params aplique su normalizacion legacy. */
    private static SphereLayout.Params decodeGeometry(int spacing, int centerY, int minRadius, int radius,
            boolean enabled, int count, int minSize, int maxSize, int minGap, int maxGap,
            int gapBlocks, int minElevation, int maxElevation) {
        if (minSize > maxSize || minGap > maxGap || minElevation > maxElevation) {
            throw new IllegalArgumentException("Inverted range in saved classic satellite settings");
        }
        return new SphereLayout.Params(spacing, centerY, minRadius, radius, enabled, count,
                minSize, maxSize, minGap, maxGap, gapBlocks, minElevation, maxElevation);
    }

    /** Solo se llama tras identificar un mundo clasico nuevo. */
    public static ClassicWorldSettings capture() {
        var geometry = SphereLayout.current().params();
        var minerals = List.<SatelliteLayers.Mineral>of();
        if (geometry.satellitesEnabled() && geometry.satellitesMaxCount() > 0) {
            minerals = SatelliteMineralCatalog.resolve(BuiltInRegistries.BLOCK,
                    SphereConfig.SATELLITES_INCLUDED_ORES.get(), SphereConfig.SATELLITES_EXCLUDED_ORES.get(),
                    SphereConfig.SATELLITES_MOD_ORE_WEIGHT.get(), SphereConfig.SATELLITES_MOD_ORE_DENSITY_PERMILLE.get())
                    .requireSelectable("Classic saved world");
        }
        return new ClassicWorldSettings(geometry, List.copyOf(SphereConfig.SPHERE_BLOCK.get()),
                SphereConfig.BEDROCK_MODE.get(), SphereConfig.ONLY_UPPER_HEMISPHERE.get(), SphereConfig.CLOSE_OPEN_TOP.get(),
                new Bridges(SphereConfig.GENERATE_BRIDGE.get(), SphereConfig.BRIDGE_RADIUS.get().intValue(), SphereConfig.BRIDGE_BLOCK.get()),
                new Ocean(SphereConfig.EDGE_OCEAN_ENABLED.get(), SphereConfig.EDGE_OCEAN_WIDTH.get(), SphereConfig.EDGE_OCEAN_DEPTH.get()),
                new Structures(SphereConfig.RESTRICT_STRUCTURES_TO_SPHERES.get(), SphereConfig.STRUCTURE_EDGE_MARGIN.get()), minerals,
                SphereConfig.UPPER_SHELL_ENABLED.get(), SphereConfig.LOWER_SHELL_ENABLED.get());
    }

    /** Un bloque desaparecido invalida la receta; nunca se sustituye silenciosamente. */
    public void validateBlocks() {
        shellBlocks.forEach(ClassicWorldSettings::requireBlock);
        if (bridges.enabled()) requireBlock(bridges.block());
        if (geometry.satellitesEnabled() && geometry.satellitesMaxCount() > 0) {
            for (var mineral : minerals) {
                requireBlock(mineral.blockId());
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mineral.blockId()));
                var state = block.defaultBlockState();
                if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || !state.canOcclude()) {
                    throw new IllegalArgumentException("Unusable saved classic mineral: " + mineral.blockId());
                }
            }
        }
    }
    private static void requireBlock(String name) {
        var id = ResourceLocation.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Missing block in saved classic recipe: " + name);
        }
    }

    /** Propiedad por generador; las caches pertenecen exclusivamente a esta receta. */
    public record Bound(ClassicWorldSettings settings, SphereLayout layout) {
        public Bound(ClassicWorldSettings settings) { this(settings, new SphereLayout(settings.geometry())); }
        public boolean isShell(BlockState state) {
            for (String name : settings.shellBlocks()) {
                if (state.is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name)))) return true;
            }
            return false;
        }
    }

    /** Referencia publicada antes de que el motor cree sus workers de generacion. */
    public interface Access {
        Bound biosphereworlds$classicWorld();
        void biosphereworlds$setClassicWorld(Bound world);
    }
}
