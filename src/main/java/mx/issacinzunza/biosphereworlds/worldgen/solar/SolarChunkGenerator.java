package mx.issacinzunza.biosphereworlds.worldgen.solar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.mixin.accessor.SolarStructureStateInvoker;
import mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog;
import mx.issacinzunza.biosphereworlds.mixin.accessor.SolarRandomStateAccess;
import mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates the vanilla generation lifecycle for Solar worlds.
 * Geometry belongs to {@link SolarLayout}, block writes to {@link SolarVolumePainter},
 * and cage behavior to {@link SolarGuardians}. Seed binding publishes one immutable
 * snapshot before generation workers can use the generator; classic Biospheres is untouched.
 */
public final class SolarChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<SolarChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.solarBiomes.source()),
            Biome.CODEC.fieldOf("void_biome").forGetter(g -> g.solarBiomes.voidBiome()),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(SolarChunkGenerator::generatorSettings),
            SolarSettings.CODEC.optionalFieldOf("solar").forGetter(g -> g.boundWorld == null && g.configureOnBind
                    ? Optional.empty() : Optional.of(g.solarSettings()))
    ).apply(i, (source, empty, noise, solar) -> new SolarChunkGenerator(new SolarBiomeSource(source, empty,
            solar.orElseGet(SolarSettings::defaults)), noise, solar.orElseGet(SolarSettings::defaults), solar.isEmpty())));

    private final SolarBiomeSource solarBiomes;
    private final SolarSettings initialSettings;
    private final boolean configureOnBind;
    private volatile BoundWorld boundWorld;

    /** All resources derived from a seed are published together, never field by field. */
    private record BoundWorld(long seed, SolarSettings settings, SolarLayout layout,
                              RandomState terrainRandom, SolarVolumePainter volume,
                              List<Holder<Structure>> strongholds) { }

    public SolarChunkGenerator(BiomeSource source, Holder<Biome> empty, Holder<NoiseGeneratorSettings> noise,
                               SolarSettings solar) {
        this(new SolarBiomeSource(source, empty, solar), noise, solar, false);
    }
    private SolarChunkGenerator(SolarBiomeSource source, Holder<NoiseGeneratorSettings> noise, SolarSettings solar, boolean configureOnBind) {
        // TerraBlender initializes only a directly exposed MultiNoiseBiomeSource.
        super(source.source() instanceof net.minecraft.world.level.biome.MultiNoiseBiomeSource ? source.source() : source, noise);
        solarBiomes = source;
        if (getBiomeSource() instanceof mx.issacinzunza.biosphereworlds.worldgen.BiosphereBiomeSourceAccess access) {
            access.biosphereworlds$setSolarBiomes(source);
        }
        initialSettings = solar;
        this.configureOnBind = configureOnBind;
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    public SolarSettings solarSettings() {
        var bound = boundWorld;
        return bound == null ? initialSettings : bound.settings();
    }

    public SolarLayout layout() {
        return world().layout();
    }

    private BoundWorld world() {
        var bound = boundWorld;
        if (bound == null) throw new IllegalStateException("Solar generator used before seed binding");
        return bound;
    }

    @Override
    public synchronized ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState random, long seed) {
        var entries = sets.listElements()
                .flatMap(holder -> holder.value().structures().stream())
                .filter(entry -> entry.structure().is(StructureTags.EYE_OF_ENDER_LOCATED))
                .distinct().toList();
        // A replacement owns its pieces and portal. Do not ask a mod that disables vanilla to generate vanilla.
        var replacements = entries.stream()
                .filter(entry -> !entry.structure().is(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,
                        ResourceLocation.withDefaultNamespace("stronghold")))).toList();
        var centralEntries = replacements.isEmpty() ? entries : replacements;
        if (centralEntries.isEmpty()) {
            throw new IllegalStateException("Solar requires a structure in #minecraft:eye_of_ender_located");
        }
        if (boundWorld == null) {
            var settings = configureOnBind ? SolarSettings.configured() : initialSettings;
            var geometry = new SolarLayout(seed, settings.geometry(), settings.layoutVersion(), settings.general().placement());
            var minerals = List.<SatelliteLayers.Mineral>of();
            // Disabled satellites must not resolve or validate an unused ore catalog.
            if (settings.geometry().satelliteCount() > 0) {
                var options = settings.general().satellites();
                var resolved = SatelliteMineralCatalog.resolve(BuiltInRegistries.BLOCK, options.included(), options.excluded(),
                        options.modWeight(), options.modDensity());
                minerals = resolved.candidates();
                BiosphereWorlds.LOGGER.info("Solar satellite minerals bound: candidates={} excluded={} ignored={}",
                        minerals, resolved.excluded(), resolved.ignored());
                if (!resolved.ignored().isEmpty()) BiosphereWorlds.LOGGER.warn(
                        "Solar satellite mineral entries ignored: {}", resolved.ignored());
                // Mineral already rejects negative weights; at least one positive weight is necessary and sufficient.
                if (minerals.stream().noneMatch(mineral -> mineral.weight() > 0)) throw new IllegalArgumentException(
                        "Solar satellite mineral selection requires at least one positive-weight candidate after exclusions; "
                                + "check satellites_included_ores, satellites_excluded_ores and satellites_mod_ore_weight in the world's settings");
            }
            var terrain = settings.layoutVersion() >= 5 ? createTerrainRandom(random, seed, geometry, settings) : null;
            var volume = new SolarVolumePainter(geometry, settings, minerals, getSeaLevel());
            solarBiomes.bind(geometry, settings);
            boundWorld = new BoundWorld(seed, settings, geometry, terrain, volume,
                    centralEntries.stream().map(StructureSet.StructureSelectionEntry::structure).distinct().toList());
        } else if (boundWorld.seed() != seed) {
            throw new IllegalStateException("Solar generator shared between different seeds");
        }
        var selected = new ArrayList<Holder<StructureSet>>();
        sets.listElements().forEach(holder -> {
            var remaining = holder.value().structures().stream()
                    .filter(entry -> !entry.structure().is(StructureTags.EYE_OF_ENDER_LOCATED)
                            && !(entry.structure().value() instanceof StrongholdStructure)).toList();
            if (!remaining.isEmpty()) selected.add(Holder.direct(new StructureSet(remaining, holder.value().placement())));
        });
        selected.add(Holder.direct(new StructureSet(centralEntries, new SolarPlacement(solarSettings().geometry()))));
        return SolarStructureStateInvoker.biosphereworlds$create(terrainRandom(random), getBiomeSource(), seed, seed, List.copyOf(selected));
    }

    /** Identity of the registered structures selected for this Solar world's central placement. */
    public boolean isCentralStronghold(Structure structure) {
        return world().strongholds().stream().anyMatch(holder -> holder.value() == structure);
    }

    /** Adapts the shared noise template without modifying the classic generator's RandomState. */
    private RandomState createTerrainRandom(RandomState original, long seed, SolarLayout geometry, SolarSettings settings) {
        var template = generatorSettings().value();
        var terrain = new NoiseGeneratorSettings(template.noiseSettings(), template.defaultBlock(), template.defaultFluid(),
                SolarDensityFunction.adapt(template.noiseRouter(), geometry, settings.environment()),
                template.surfaceRule(), template.spawnTarget(), template.seaLevel(), template.disableMobGeneration(),
                template.isAquifersEnabled(), template.oreVeinsEnabled(), template.useLegacyRandomSource());
        return RandomState.create(terrain, ((SolarRandomStateAccess) (Object) original).biosphereworlds$noiseParameters(), seed);
    }

    /** Returns the world's pre-resolved shell state for portal-placement protection. */
    public BlockState shellState(SolarLayout.Body body) { return world().volume().shellState(body); }

    private RandomState terrainRandom(RandomState original) {
        var bound = world().terrainRandom();
        return bound == null ? original : bound;
    }

    private void mark(ChunkAccess chunk) { ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(layout()); }

    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState random, Blender blender, StructureManager structures, ChunkAccess chunk) {
        mark(chunk);
        return super.createBiomes(terrainRandom(random), blender, structures, chunk);
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        mark(chunk);
        if (!touchesPlanet(chunk)) return CompletableFuture.completedFuture(chunk);
        return super.fillFromNoise(blender, terrainRandom(random), structures, chunk).thenApply(generated -> {
            paintVolume(generated, SolarVolumePainter.Pass.NOISE);
            return generated;
        });
    }
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
        mark(chunk);
        if (touchesPlanet(chunk)) super.buildSurface(level, structures, terrainRandom(random), chunk);
        paintVolume(chunk, SolarVolumePainter.Pass.SURFACE);
    }
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        mark(chunk);
        // Classic Biospheres only executes its shell/bridge carvers, not additional vanilla biome carvers.
        // For new Solar worlds the shared density supplies caves; the volume painter supplies shells and moons.
        if (solarSettings().layoutVersion() < 5 && touchesPlanet(chunk)) super.applyCarvers(level, seed, terrainRandom(random), biomes, structures, chunk, step);
        paintVolume(chunk, SolarVolumePainter.Pass.SHELL);
    }
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        mark(chunk);
        if (touchesPlanet(chunk)) super.applyBiomeDecoration(level, chunk, structures);
        paintVolume(chunk, SolarVolumePainter.Pass.SHELL);
        SolarGuardians.placeCages(chunk, this);
    }

    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
                                           StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
        mark(chunk);
        super.createStructures(registries, state, structures, chunk, templates);
        var center = layout().systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()).central();
        if (chunk.getPos().x == Math.floorDiv((int) center.x(), 16)
                && chunk.getPos().z == Math.floorDiv((int) center.z(), 16)) {
            boolean valid = world().strongholds().stream().anyMatch(holder -> {
                var start = structures.getStartForStructure(SectionPos.bottomOf(chunk), holder.value(), chunk);
                return start != null && start.isValid();
            });
            if (!valid) {
                throw new IllegalStateException("Solar central stronghold failed biome/structure/containment validation at "
                        + chunk.getPos() + "; candidates=" + world().strongholds());
            }
        }
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        var original = super.getBaseColumn(x, z, height, terrainRandom(random));
        for (int y = height.getMinBuildHeight(); y < height.getMaxBuildHeight(); y++) {
            if (layout().bodyAt(x, y, z, 2) == null) original.setBlock(y, Blocks.AIR.defaultBlockState());
        }
        return original;
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        NoiseColumn column = getBaseColumn(x, z, height, random);
        for (int y = height.getMaxBuildHeight() - 1; y >= height.getMinBuildHeight(); y--) {
            if (type.isOpaque().test(column.getBlock(y))) return y + 1;
        }
        return height.getMinBuildHeight();
    }

    private void paintVolume(ChunkAccess chunk, SolarVolumePainter.Pass pass) { world().volume().paint(chunk, pass); }

    private boolean touchesPlanet(ChunkAccess chunk) {
        return world().volume().touchesPlanet(chunk);
    }

    /** Delegates heightmap filtering to the volume module used for block generation. */
    public static BlockState heightmapState(ChunkAccess chunk, BlockPos pos, BlockState state) {
        return SolarVolumePainter.heightmapState(chunk, pos, state);
    }
}
