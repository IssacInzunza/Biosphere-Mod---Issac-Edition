package mx.issacinzunza.biosphereworlds.worldgen.solar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mx.issacinzunza.biosphereworlds.config.SolarConfig;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import java.util.List;

/** Codec snapshots are saved with the generator, rather than rereading geometry on restart. */
public record SolarSettings(SolarLayout.Params geometry, boolean climateEnabled,
                            SolarLayout.Climate centralClimate, Guardians guardians, Environment environment,
                            General general, int layoutVersion) {
    public SolarSettings {
        if (layoutVersion < 1 || layoutVersion > 4) throw new IllegalArgumentException("Unknown Solar layout version");
    }
    public SolarSettings(SolarLayout.Params geometry, boolean climateEnabled, SolarLayout.Climate centralClimate,
                         Guardians guardians, Environment environment) {
        this(geometry, climateEnabled, centralClimate, guardians, environment, General.defaults(), 2);
    }

    public record Satellites(int minSize, int maxSize, int minGapPercent, int maxGapPercent,
                             List<String> included, List<String> excluded, int modWeight, int modDensity) {
        public Satellites {
            new SolarLayout.PlacementOptions(64, minSize, maxSize, minGapPercent, maxGapPercent);
            if (modWeight < 0 || modWeight > 1000 || modDensity < 0 || modDensity > 1000) throw new IllegalArgumentException("Invalid Solar mineral settings");
            included = List.copyOf(included);
            excluded = List.copyOf(excluded);
        }
        public static final Codec<Satellites> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min_size").forGetter(Satellites::minSize),
                Codec.INT.fieldOf("max_size").forGetter(Satellites::maxSize),
                Codec.INT.fieldOf("min_gap_percent").forGetter(Satellites::minGapPercent),
                Codec.INT.fieldOf("max_gap_percent").forGetter(Satellites::maxGapPercent),
                Codec.STRING.listOf().fieldOf("included").forGetter(Satellites::included),
                Codec.STRING.listOf().fieldOf("excluded").forGetter(Satellites::excluded),
                Codec.INT.fieldOf("mod_weight").forGetter(Satellites::modWeight),
                Codec.INT.fieldOf("mod_density").forGetter(Satellites::modDensity)
        ).apply(i, Satellites::new));
    }

    public record General(int centerY, List<String> shellBlocks, SphereConfig.BedrockMode bedrockMode, Satellites satellites, boolean closeOpenTop) {
        public General(int centerY, List<String> shellBlocks, SphereConfig.BedrockMode bedrockMode, Satellites satellites) {
            this(centerY, shellBlocks, bedrockMode, satellites, false);
        }
        public General {
            if (centerY < -64 || centerY > 319) throw new IllegalArgumentException("Invalid Solar center Y");
            shellBlocks = List.copyOf(shellBlocks);
        }
        public SolarLayout.PlacementOptions placement() {
            return new SolarLayout.PlacementOptions(centerY, satellites.minSize, satellites.maxSize,
                    satellites.minGapPercent, satellites.maxGapPercent, closeOpenTop);
        }
        public static General legacy() {
            return new General(64, List.of("minecraft:glass"), SphereConfig.BedrockMode.FLOOR,
                    new Satellites(8, 12, 25, 75, List.of(), List.of(), 3, 20));
        }
        public static General defaults() {
            return new General(64, List.of("minecraft:glass"), SphereConfig.BedrockMode.FLOOR,
                    new Satellites(8, 12, 25, 75, List.of(), List.of("minecraft:ancient_debris", "minecraft:nether_quartz_ore",
                            "minecraft:nether_gold_ore", "#c:ores_in_ground/deepslate"), 3, 20));
        }
        public static final Codec<General> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("center_y").forGetter(General::centerY),
                Codec.STRING.listOf().fieldOf("shell_blocks").forGetter(General::shellBlocks),
                Codec.STRING.xmap(SphereConfig.BedrockMode::valueOf, Enum::name).fieldOf("bedrock_mode").forGetter(General::bedrockMode),
                Satellites.CODEC.fieldOf("satellites").forGetter(General::satellites),
                Codec.BOOL.optionalFieldOf("close_open_top", false).forGetter(General::closeOpenTop)
        ).apply(i, General::new));
    }
    public static final Codec<SolarLayout.Params> GEOMETRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("system_radius").forGetter(SolarLayout.Params::systemRadius),
            Codec.INT.fieldOf("system_gap").forGetter(SolarLayout.Params::systemGap),
            Codec.INT.fieldOf("planet_count").forGetter(SolarLayout.Params::planetCount),
            Codec.INT.fieldOf("min_radius").forGetter(SolarLayout.Params::minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(SolarLayout.Params::maxRadius),
            Codec.INT.fieldOf("central_radius").forGetter(SolarLayout.Params::centralRadius),
            Codec.INT.fieldOf("min_gap").forGetter(SolarLayout.Params::minGap),
            Codec.INT.fieldOf("max_gap").forGetter(SolarLayout.Params::maxGap),
            Codec.BOOL.fieldOf("cold_inside").forGetter(SolarLayout.Params::coldInside),
            Codec.INT.fieldOf("inner_percent").forGetter(SolarLayout.Params::innerPercent),
            Codec.INT.fieldOf("middle_percent").forGetter(SolarLayout.Params::middlePercent),
            Codec.INT.fieldOf("outer_percent").forGetter(SolarLayout.Params::outerPercent),
            Codec.INT.fieldOf("satellite_count").forGetter(SolarLayout.Params::satelliteCount),
            Codec.INT.fieldOf("satellite_min_elevation").forGetter(SolarLayout.Params::satelliteMinElevation),
            Codec.INT.fieldOf("satellite_max_elevation").forGetter(SolarLayout.Params::satelliteMaxElevation),
            Codec.INT.fieldOf("satellite_gap").forGetter(SolarLayout.Params::satelliteGap)
    ).apply(i, SolarLayout.Params::new));

    public record Guardians(boolean enabled, boolean ghasts, boolean phantoms,
                            int radius, int ghastMax, int phantomMax, int interval) {
        public Guardians {
            if (radius < 8 || radius > 128 || ghastMax < 0 || ghastMax > 16
                    || phantomMax < 0 || phantomMax > 32 || interval < 100 || interval > 72000) {
                throw new IllegalArgumentException("Invalid Solar guardian limits");
            }
        }
        public static final Codec<Guardians> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("enabled").forGetter(Guardians::enabled),
                Codec.BOOL.fieldOf("ghasts").forGetter(Guardians::ghasts),
                Codec.BOOL.fieldOf("phantoms").forGetter(Guardians::phantoms),
                Codec.INT.fieldOf("radius").forGetter(Guardians::radius),
                Codec.INT.fieldOf("ghast_max").forGetter(Guardians::ghastMax),
                Codec.INT.fieldOf("phantom_max").forGetter(Guardians::phantomMax),
                Codec.INT.fieldOf("interval").forGetter(Guardians::interval)
        ).apply(i, Guardians::new));
    }

    public record Environment(boolean edgeOceanEnabled, int edgeOceanWidth, int edgeOceanDepth, boolean bedrockLiner) {
        public Environment {
            if (edgeOceanWidth < 0 || edgeOceanWidth > 100 || edgeOceanDepth < 0 || edgeOceanDepth > 100) {
                throw new IllegalArgumentException("Invalid Solar ocean band");
            }
        }
        public static final Codec<Environment> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("edge_ocean_enabled").forGetter(Environment::edgeOceanEnabled),
                Codec.INT.fieldOf("edge_ocean_width").forGetter(Environment::edgeOceanWidth),
                Codec.INT.fieldOf("edge_ocean_depth").forGetter(Environment::edgeOceanDepth),
                Codec.BOOL.fieldOf("bedrock_liner").forGetter(Environment::bedrockLiner)
        ).apply(i, Environment::new));
    }

    public static final Codec<SolarSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            GEOMETRY_CODEC.fieldOf("geometry").forGetter(SolarSettings::geometry),
            Codec.BOOL.fieldOf("climate_enabled").forGetter(SolarSettings::climateEnabled),
            Codec.STRING.xmap(SolarLayout.Climate::valueOf, Enum::name).fieldOf("central_climate").forGetter(SolarSettings::centralClimate),
            Guardians.CODEC.fieldOf("guardians").forGetter(SolarSettings::guardians),
            Environment.CODEC.fieldOf("environment").forGetter(SolarSettings::environment),
            General.CODEC.optionalFieldOf("general", General.legacy()).forGetter(SolarSettings::general),
            Codec.intRange(1, 4).optionalFieldOf("layout_version", 1).forGetter(SolarSettings::layoutVersion)
    ).apply(i, SolarSettings::new));

    public static SolarSettings configured() { return SolarConfig.snapshot(); }
    public static SolarSettings defaults() {
        return new SolarSettings(SolarLayout.Params.defaults(), true, SolarLayout.Climate.HOT,
                new Guardians(true, true, true, 48, 3, 6, 400), new Environment(false, 20, 20, false), General.defaults(), 2);
    }
}
