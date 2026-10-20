package mx.issacinzunza.biosphereworlds.worldgen.solar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mx.issacinzunza.biosphereworlds.mixin.accessor.SolarStructureStateInvoker;
import mx.issacinzunza.biosphereworlds.worldgen.MineralCatalog;
import mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
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
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Own generator, without marking anything as classic Biospheres. */
public final class SolarChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<SolarChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.solarBiomes.source()),
            Biome.CODEC.fieldOf("void_biome").forGetter(g -> g.solarBiomes.voidBiome()),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(SolarChunkGenerator::generatorSettings),
            SolarSettings.CODEC.optionalFieldOf("solar").forGetter(g -> g.layout == null && g.configureOnBind
                    ? Optional.empty() : Optional.of(g.solarSettings))
    ).apply(i, (source, empty, noise, solar) -> new SolarChunkGenerator(new SolarBiomeSource(source, empty,
            solar.orElseGet(SolarSettings::defaults)), noise, solar.orElseGet(SolarSettings::defaults), solar.isEmpty())));

    private final SolarBiomeSource solarBiomes;
    private volatile SolarSettings solarSettings;
    private final boolean configureOnBind;
    private volatile SolarLayout layout;
    private long boundSeed;
    private Holder<Structure> stronghold;
    private java.util.List<SatelliteLayers.Mineral> minerals;

    public SolarChunkGenerator(BiomeSource source, Holder<Biome> empty, Holder<NoiseGeneratorSettings> noise,
                               SolarSettings solar) {
        this(new SolarBiomeSource(source, empty, solar), noise, solar, false);
    }
    private SolarChunkGenerator(SolarBiomeSource source, Holder<NoiseGeneratorSettings> noise, SolarSettings solar, boolean configureOnBind) {
        super(source, noise);
        solarBiomes = source;
        solarSettings = solar;
        this.configureOnBind = configureOnBind;
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    public SolarSettings solarSettings() { return solarSettings; }
    public SolarLayout layout() {
        SolarLayout result = layout;
        if (result == null) throw new IllegalStateException("Solar generator used before seed binding");
        return result;
    }

    @Override public synchronized ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState random, long seed) {
        if (layout == null) {
            if (configureOnBind) solarSettings = SolarSettings.configured();
            boundSeed = seed;
            var geometry = new SolarLayout(seed, solarSettings.geometry(), solarSettings.layoutVersion(), solarSettings.general().placement());
            solarBiomes.bind(geometry, solarSettings);
            layout = geometry;
        } else if (boundSeed != seed) throw new IllegalStateException("Solar generator shared between different seeds");
        var selected = new ArrayList<Holder<StructureSet>>();
        sets.listElements().forEach(holder -> {
            for (var entry : holder.value().structures()) {
                if (entry.structure().is(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("stronghold")))) {
                    stronghold = entry.structure();
                }
            }
            var entries = holder.value().structures().stream()
                    .filter(e -> !(e.structure().value() instanceof StrongholdStructure)).toList();
            if (!entries.isEmpty()) selected.add(Holder.direct(new StructureSet(entries, holder.value().placement())));
        });
        if (stronghold == null || !(stronghold.value() instanceof StrongholdStructure)) {
            throw new IllegalStateException("Solar requires the vanilla stronghold structure; a datapack replaced or removed it");
        }
        selected.add(Holder.direct(new StructureSet(stronghold, new SolarPlacement(solarSettings.geometry()))));
        if (minerals == null) {
            var tag = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores_in_ground/stone"));
            var ids = new java.util.TreeSet<String>();
            BuiltInRegistries.BLOCK.getTag(tag).ifPresent(ores -> ores.forEach(b -> {
                var state = b.value().defaultBlockState();
                if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.canOcclude()) {
                    ids.add(BuiltInRegistries.BLOCK.getKey(b.value()).toString());
                }
            }));
            var options = solarSettings.general().satellites();
            var included = resolveOres(options.included());
            var excluded = resolveOres(options.excluded());
            minerals = MineralCatalog.assemble(SatelliteLayers.VANILLA_MINERALS, ids, included, excluded, options.modWeight(), options.modDensity());
            if (minerals.isEmpty()) throw new IllegalArgumentException("Solar satellite mineral selection is empty after exclusions");
        }
        return SolarStructureStateInvoker.biosphereworlds$create(random, getBiomeSource(), seed, seed, java.util.List.copyOf(selected));
    }

    private static java.util.Set<String> resolveOres(java.util.List<String> entries) {
        var result = new java.util.TreeSet<String>();
        for (String raw : entries) {
            boolean tag = raw.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id == null) continue;
            if (tag) BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).ifPresent(blocks -> blocks.forEach(b -> {
                var state = b.value().defaultBlockState();
                if (!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.canOcclude()) {
                    result.add(BuiltInRegistries.BLOCK.getKey(b.value()).toString());
                }
            }));
            else if (BuiltInRegistries.BLOCK.containsKey(id)) {
                var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
                if (!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.canOcclude()) result.add(id.toString());
            }
        }
        return result;
    }

    public BlockState shellState(SolarLayout.Body body) {
        var blocks = solarSettings.general().shellBlocks();
        if (blocks.isEmpty()) return Blocks.GLASS.defaultBlockState();
        var id = ResourceLocation.tryParse(blocks.get((int) Math.floorMod(body.id(), blocks.size())));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Blocks.GLASS.defaultBlockState();
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        return state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME) ? Blocks.GLASS.defaultBlockState() : state;
    }

    private void mark(ChunkAccess chunk) { ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(layout()); }

    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState random, Blender blender, StructureManager structures, ChunkAccess chunk) {
        mark(chunk);
        return super.createBiomes(random, blender, structures, chunk);
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        mark(chunk);
        if (!touchesPlanet(chunk)) return CompletableFuture.completedFuture(chunk);
        return super.fillFromNoise(blender, random, structures, chunk).thenApply(generated -> {
            finishVolume(generated, false);
            return generated;
        });
    }
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
        mark(chunk);
        if (touchesPlanet(chunk)) super.buildSurface(level, structures, random, chunk);
        finishVolume(chunk, false, true);
    }
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        mark(chunk);
        if (touchesPlanet(chunk)) super.applyCarvers(level, seed, random, biomes, structures, chunk, step);
        finishVolume(chunk, true);
    }
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        mark(chunk);
        if (touchesPlanet(chunk)) super.applyBiomeDecoration(level, chunk, structures);
        finishVolume(chunk, true);
        mx.issacinzunza.biosphereworlds.event.SolarEvents.placeGuardianSpawners(chunk, this);
    }

    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
                                           StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
        mark(chunk);
        super.createStructures(registries, state, structures, chunk, templates);
        var center = layout().systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()).central();
        if (chunk.getPos().x == Math.floorDiv((int) center.x(), 16)
                && chunk.getPos().z == Math.floorDiv((int) center.z(), 16)) {
            var start = structures.getStartForStructure(SectionPos.bottomOf(chunk), stronghold.value(), chunk);
            if (start == null || !start.isValid()) {
                throw new IllegalStateException("Solar central stronghold failed biome/structure validation at " + chunk.getPos());
            }
        }
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        var original = super.getBaseColumn(x, z, height, random);
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

    private void finishVolume(ChunkAccess chunk, boolean shell) {
        finishVolume(chunk, shell, false);
    }

    private void finishVolume(ChunkAccess chunk, boolean shell, boolean centralSurface) {
        var system = layout().systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
        var bodies = system.bodies().stream().filter(b -> touches(chunk, b.x(), b.z(), b.radius())).toList();
        var moons = system.moons().stream().filter(m -> touches(chunk, m.sphere().centerX(), m.sphere().centerZ(), m.sphere().radius())).toList();
        if (bodies.isEmpty() && moons.isEmpty()) return;
        boolean extreme = centralSurface && solarSettings.layoutVersion() >= 2
                && solarSettings.centralClimate() != SolarLayout.Climate.TEMPERATE && bodies.stream().anyMatch(SolarLayout.Body::central);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int surfaceY = Integer.MIN_VALUE;
                if (extreme) {
                    for (int y = chunk.getMaxBuildHeight() - 1; y >= 56; y--) {
                        var state = chunk.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && state.getFluidState().isEmpty()) { surfaceY = y; break; }
                    }
                }
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                    SolarLayout.Body containing = null;
                    for (var body : bodies) {
                        if (body.contains(x, y, z, 0)) { containing = body; break; }
                    }
                    pos.set(x, y, z);
                    BlockState replacement = null;
                    if (containing == null) replacement = Blocks.AIR.defaultBlockState();
                    else if (shell && layout().shellBoundary(containing, x, y, z)) replacement = shellState(containing);
                    else if (shell && SolarTerrain.bedrockLiner(containing, x, y, z, solarSettings.environment().bedrockLiner())) {
                        replacement = Blocks.BEDROCK.defaultBlockState();
                    } else if (!shell) {
                        var environment = solarSettings.environment();
                        int ocean = SolarTerrain.oceanOverride(containing, x, y, z, environment.edgeOceanEnabled(),
                                environment.edgeOceanWidth(), environment.edgeOceanDepth(), getSeaLevel());
                        if (ocean == 1) replacement = Blocks.AIR.defaultBlockState();
                        else if (ocean == 2) replacement = Blocks.WATER.defaultBlockState();
                        var existing = replacement == null ? chunk.getBlockState(pos) : replacement;
                        if (solarSettings.layoutVersion() >= 2 && solarSettings.general().bedrockMode()
                                == mx.issacinzunza.biosphereworlds.config.SphereConfig.BedrockMode.NONE && existing.is(Blocks.BEDROCK)) {
                            replacement = Blocks.DEEPSLATE.defaultBlockState();
                        }
                        if (extreme && containing.central() && containing.contains(x, y, z, 2)) {
                            boolean cold = solarSettings.centralClimate() == SolarLayout.Climate.COLD;
                            if (SolarTerrain.centralFluid(y, existing.is(Blocks.WATER))) {
                                replacement = cold ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.LAVA.defaultBlockState();
                            } else if (SolarTerrain.centralSurface(y, surfaceY) && !existing.isAir()
                                    && existing.getFluidState().isEmpty() && !existing.is(Blocks.BEDROCK)) {
                                replacement = cold ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
                            }
                        }
                    }
                    if (replacement != null && !chunk.getBlockState(pos).equals(replacement)) chunk.setBlockState(pos, replacement, false);
                }
            }
        }
        if (shell) {
            for (var moon : moons) {
                var s = moon.sphere();
                var layers = SatelliteLayers.layers(s.radius());
                var mineral = layers.hasCore() ? SatelliteLayers.selectMineral(s.seed(), minerals) : null;
                BlockState satelliteShell = shellState(system.bodies().stream().filter(b -> b.id() == moon.planetId()).findFirst().orElseThrow());
                BlockState ore = mineral == null ? Blocks.STONE.defaultBlockState()
                        : BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mineral.blockId())).defaultBlockState();
                for (int x = Math.max(chunk.getPos().getMinBlockX(), (int) Math.floor(s.centerX() - s.radius()));
                     x <= Math.min(chunk.getPos().getMaxBlockX(), (int) Math.ceil(s.centerX() + s.radius())); x++) {
                    for (int z = Math.max(chunk.getPos().getMinBlockZ(), (int) Math.floor(s.centerZ() - s.radius()));
                         z <= Math.min(chunk.getPos().getMaxBlockZ(), (int) Math.ceil(s.centerZ() + s.radius())); z++) {
                        for (int y = (int) Math.floor(s.centerY() - s.radius()); y <= Math.ceil(s.centerY() + s.radius()); y++) {
                            double d = Math.pow(x - s.centerX(), 2) + Math.pow(y - s.centerY(), 2) + Math.pow(z - s.centerZ(), 2);
                            if (d > (double) s.radius() * s.radius()) continue;
                            BlockState state = switch (SatelliteLayers.regionAt(layers, d)) {
                                case SHELL -> satelliteShell;
                                case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
                                case STONE -> Blocks.STONE.defaultBlockState();
                                case CORE -> mineral != null && SatelliteLayers.isOre(s.seed(), x, y, z, mineral.densityPermille())
                                        ? ore : Blocks.STONE.defaultBlockState();
                            };
                            chunk.setBlockState(pos.set(x, y, z), state, false);
                        }
                    }
                }
            }
        }
        // primeHeightmaps doesn't reset columns that have become completely empty.
        for (var type : Heightmap.Types.values()) java.util.Arrays.fill(chunk.getOrCreateHeightmapUnprimed(type).getRawData(), 0);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
    }

    private boolean touchesPlanet(ChunkAccess chunk) {
        return layout().systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()).bodies().stream()
                .anyMatch(b -> touches(chunk, b.x(), b.z(), b.radius()));
    }
    private static boolean touches(ChunkAccess chunk, double x, double z, int radius) {
        double dx = Math.max(Math.max(chunk.getPos().getMinBlockX() - x, 0), x - chunk.getPos().getMaxBlockX());
        double dz = Math.max(Math.max(chunk.getPos().getMinBlockZ() - z, 0), z - chunk.getPos().getMaxBlockZ());
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    public static BlockState heightmapState(ChunkAccess chunk, BlockPos pos, BlockState state) {
        SolarLayout geometry = ((SolarChunkAccess) chunk).biosphereworlds$solarLayout();
        if (geometry == null) return state;
        for (var moon : geometry.systemAtBlock(pos.getX(), pos.getZ()).moons()) {
            var s = moon.sphere();
            if (Math.pow(pos.getX() - s.centerX(), 2) + Math.pow(pos.getY() - s.centerY(), 2) + Math.pow(pos.getZ() - s.centerZ(), 2)
                    <= (double) s.radius() * s.radius()) return Blocks.AIR.defaultBlockState();
        }
        if (geometry.version() == 1 && !state.is(Blocks.GLASS) && !state.is(Blocks.BEDROCK)) return state;
        var body = geometry.bodyAt(pos.getX(), pos.getY(), pos.getZ(), 0);
        if (body != null && state.is(Blocks.BEDROCK) && SolarTerrain.bedrockLiner(body, pos.getX(), pos.getY(), pos.getZ(), true)) {
            return Blocks.AIR.defaultBlockState();
        }
        return body != null && geometry.shellBoundary(body, pos.getX(), pos.getY(), pos.getZ()) ? Blocks.AIR.defaultBlockState() : state;
    }
}
