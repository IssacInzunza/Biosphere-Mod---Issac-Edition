package mx.issacinzunza.biosphereworlds.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.density.ClassicDensityFunction;
import mx.issacinzunza.biosphereworlds.density.MultipleSpheresDistanceFunction;
import mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean;
import mx.issacinzunza.biosphereworlds.density.SphereEdgeOceanFunction;
import mx.issacinzunza.biosphereworlds.registry.ModCarvers;
import mx.issacinzunza.biosphereworlds.worldgen.*;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Persistencia real y contratos por mundo; todas las partidas son fixtures temporales. */
@GameTestHolder(BiosphereWorlds.MODID)
@PrefixGameTestTemplate(false)
public final class ClassicPersistenceGameTests {
    private ClassicPersistenceGameTests() { }

    @GameTest(template = "solar_empty")
    public static void savedRecipeRejectsCorruptionAndPreservesLegacy(GameTestHelper helper) throws Exception {
        var root = Files.createDirectories(Path.of("classic-data-" + UUID.randomUUID()));
        var file = root.resolve(ClassicWorldData.ID + ".dat");
        var registry = helper.getLevel().registryAccess();
        var legacy = new DimensionDataStorage(root.toFile(), DataFixers.getDataFixer(), registry);
        helper.assertTrue(ClassicWorldData.load(legacy, file, false, () -> { throw new AssertionError("Legacy read TOML"); }) == null,
                "An old world without a recipe must remain legacy");
        legacy.save();
        IOUtilities.waitUntilIOWorkerComplete();
        helper.assertTrue(!Files.exists(file), "Legacy world acquired a saved recipe");

        var created = new DimensionDataStorage(root.toFile(), DataFixers.getDataFixer(), registry);
        var settings = ClassicWorldSettings.capture();
        var first = ClassicWorldData.load(created, file, true, () -> settings);
        created.save();
        IOUtilities.waitUntilIOWorkerComplete();
        helper.assertTrue(Files.isRegularFile(file), "New recipe was not saved");
        var reopened = new DimensionDataStorage(root.toFile(), DataFixers.getDataFixer(), registry);
        var restored = ClassicWorldData.load(reopened, file, false, () -> { throw new AssertionError("Reopen read TOML"); });
        helper.assertTrue(restored.settings().equals(first.settings()), "Saved recipe changed on disk round trip");
        helper.assertTrue(restored.settings().minerals().equals(settings.minerals()), "Resolved mineral order or weights changed");

        var valid = new CompoundTag();
        valid.putInt("schema_version", 1);
        valid.put("settings", ClassicWorldSettings.CODEC.encodeStart(NbtOps.INSTANCE, settings).getOrThrow());
        for (int defect = 0; defect < 4; defect++) {
            var bad = valid.copy();
            if (defect == 0) bad.putInt("schema_version", 999);
            if (defect == 1) bad.getCompound("settings").getCompound("geometry").putInt("min_radius", 46340);
            if (defect == 2) {
                var missing = new net.minecraft.nbt.ListTag();
                missing.add(net.minecraft.nbt.StringTag.valueOf("missing:classic_shell"));
                bad.getCompound("settings").put("shell_blocks", missing);
            }
            if (defect == 3) bad.remove("settings");
            var outer = new CompoundTag();
            outer.put("data", bad);
            NbtIo.writeCompressed(outer, file);
            boolean rejected = false;
            try {
                ClassicWorldData.load(new DimensionDataStorage(root.toFile(), DataFixers.getDataFixer(), registry), file, false,
                        () -> { throw new AssertionError("Bad recipe fell back to TOML"); });
            } catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid recipe silently became legacy, defect=" + defect);
        }
        Files.writeString(file, "not NBT");
        boolean corruptRejected = false;
        try {
            ClassicWorldData.load(new DimensionDataStorage(root.toFile(), DataFixers.getDataFixer(), registry), file, true,
                    () -> { throw new AssertionError("Corrupt recipe was replaced"); });
        } catch (IllegalStateException expected) { corruptRejected = true; }
        helper.assertTrue(corruptRejected, "Unreadable existing recipe must fail explicitly");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void savedDensityMatchesLegacyBits(GameTestHelper helper) {
        boolean previous = SphereConfig.EDGE_OCEAN_ENABLED.get();
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                SphereConfig.EDGE_OCEAN_ENABLED.set(enabled);
                var bound = new ClassicWorldSettings.Bound(ClassicWorldSettings.capture());
                var legacyDistance = new MultipleSpheresDistanceFunction(500, 128, 62);
                var savedDistance = new ClassicDensityFunction(DensityFunctions.zero(), bound, null);
                for (int x = -1027; x <= 1027; x += 37) for (int y = -64; y <= 320; y += 29) {
                    var point = new DensityFunction.SinglePointContext(x, y, -x + 11);
                    helper.assertTrue(Double.doubleToRawLongBits(legacyDistance.compute(point)) == Double.doubleToRawLongBits(savedDistance.compute(point)),
                            "Saved distance changed classic arithmetic");
                    for (var mode : SphereEdgeOcean.Mode.values()) {
                        var input = DensityFunctions.constant(-0.25);
                        var legacy = new SphereEdgeOceanFunction(input, mode);
                        var saved = new ClassicDensityFunction(input, bound, mode);
                        helper.assertTrue(Double.doubleToRawLongBits(legacy.compute(point)) == Double.doubleToRawLongBits(saved.compute(point)),
                                "Saved ocean changed classic arithmetic");
                    }
                }
                var visited = new java.util.concurrent.atomic.AtomicBoolean();
                var input = DensityFunctions.constant(-0.375);
                new ClassicDensityFunction(input, bound, SphereEdgeOcean.Mode.FLOOR).mapAll(function -> {
                    if (function == input) visited.set(true);
                    return function;
                });
                helper.assertTrue(visited.get(), "Saved density did not visit its child");
            }
        } finally { SphereConfig.EDGE_OCEAN_ENABLED.set(previous); }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void realWorldsKeepIndependentRecipesAfterReopen(GameTestHelper helper) throws Exception {
        var oldRadius = SphereConfig.RADIUS.get();
        var oldMin = SphereConfig.MIN_RADIUS.get();
        var oldShells = SphereConfig.SPHERE_BLOCK.get();
        boolean oldBridges = SphereConfig.GENERATE_BRIDGE.get();
        boolean oldOcean = SphereConfig.EDGE_OCEAN_ENABLED.get();
        boolean oldDebug = SphereConfig.DEBUG_BIOME_LOOKUPS.get();
        var oldIncludes = SphereConfig.SATELLITES_INCLUDED_ORES.get();
        var oldExcludes = SphereConfig.SATELLITES_EXCLUDED_ORES.get();
        int oldWeight = SphereConfig.SATELLITES_MOD_ORE_WEIGHT.get();
        var root = Files.createDirectories(Path.of("classic-worlds-" + UUID.randomUUID()));
        try {
            SphereConfig.RADIUS.set(128);
            SphereConfig.MIN_RADIUS.set(128);
            SphereConfig.SPHERE_BLOCK.set(List.of("minecraft:glass"));
            SphereConfig.GENERATE_BRIDGE.set(true);
            SphereConfig.EDGE_OCEAN_ENABLED.set(false);
            ClassicWorldSettings saved;
            Image baseline;
            try (var first = open(helper, root, "first", true, true);
                 var legacy = open(helper, root, "legacy", false, true)) {
                var generator = first.level().getChunkSource().getGenerator();
                var bound = BiosphereWorldgen.classicWorld(generator);
                helper.assertTrue(bound != null, "Constructor failed to bind a new classic world");
                saved = bound.settings();
                helper.assertTrue(BiosphereWorldgen.classicWorld(legacy.level().getChunkSource().getGenerator()) == null,
                        "Constructor migrated an old world");
                baseline = generate(helper, first.level());
                assertImage(helper, baseline, generate(helper, legacy.level()), "Fresh snapshot changed legacy generation");
                first.level().getDataStorage().save();
                IOUtilities.waitUntilIOWorkerComplete();

                SphereConfig.RADIUS.set(220);
                SphereConfig.MIN_RADIUS.set(220);
                SphereConfig.SPHERE_BLOCK.set(List.of("minecraft:blue_stained_glass"));
                SphereConfig.GENERATE_BRIDGE.set(false);
                SphereConfig.EDGE_OCEAN_ENABLED.set(true);
                try (var second = open(helper, root, "second", true, true);
                     var vanilla = open(helper, root, "vanilla", true, false)) {
                    var other = BiosphereWorldgen.classicWorld(second.level().getChunkSource().getGenerator());
                    helper.assertTrue(other != null && other.layout().params().maxRadius() == 220, "Second world reused the first recipe");
                    helper.assertTrue(bound.layout().params().maxRadius() == 128, "TOML modified a bound world");
                    helper.assertTrue(BiosphereWorldgen.classicLayout(legacy.level().getChunkSource().getGenerator()).params().maxRadius() == 220,
                            "Legacy world stopped following TOML");
                    helper.assertTrue(BiosphereWorldgen.classicWorld(vanilla.level().getChunkSource().getGenerator()) == null,
                            "Vanilla acquired classic state");
                    vanilla.level().getDataStorage().save();
                    IOUtilities.waitUntilIOWorkerComplete();
                    helper.assertTrue(!Files.exists(root.resolve("vanilla/data/" + ClassicWorldData.ID + ".dat")), "Vanilla wrote a classic recipe");
                    assertImage(helper, baseline, generate(helper, first.level()), "Live TOML changed existing world's blocks/biomes/maps");
                    helper.assertTrue(!baseline.blocks().equals(generate(helper, second.level()).blocks()), "Distinct recipes produced an indistinguishable fixture");
                }
            }
            // Una receta guardada valida tampoco debe depender de un TOML posterior invalido.
            SphereConfig.RADIUS.set(20);
            SphereConfig.MIN_RADIUS.set(300);
            SphereConfig.DEBUG_BIOME_LOOKUPS.set(true);
            SphereConfig.SATELLITES_INCLUDED_ORES.set(List.of());
            SphereConfig.SATELLITES_EXCLUDED_ORES.set(SatelliteLayers.VANILLA_MINERALS.stream().map(SatelliteLayers.Mineral::blockId).toList());
            SphereConfig.SATELLITES_MOD_ORE_WEIGHT.set(0);
            try (var reopened = open(helper, root, "first", false, true)) {
                // El caso DEBUG protege la vinculacion inicial; no inundar el log al muestrear todo el chunk.
                SphereConfig.DEBUG_BIOME_LOOKUPS.set(false);
                helper.assertTrue(BiosphereWorldgen.classicWorld(reopened.level().getChunkSource().getGenerator()).settings().equals(saved),
                        "Reopened constructor adopted current TOML");
                assertImage(helper, baseline, generate(helper, reopened.level()), "Reopened world changed generated blocks/biomes/maps");
            }
            helper.assertTrue(!Files.exists(root.resolve("legacy/data/" + ClassicWorldData.ID + ".dat")), "Legacy fixture was silently migrated");
        } finally {
            SphereConfig.RADIUS.set(oldRadius);
            SphereConfig.MIN_RADIUS.set(oldMin);
            SphereConfig.SPHERE_BLOCK.set(oldShells);
            SphereConfig.GENERATE_BRIDGE.set(oldBridges);
            SphereConfig.EDGE_OCEAN_ENABLED.set(oldOcean);
            SphereConfig.DEBUG_BIOME_LOOKUPS.set(oldDebug);
            SphereConfig.SATELLITES_INCLUDED_ORES.set(oldIncludes);
            SphereConfig.SATELLITES_EXCLUDED_ORES.set(oldExcludes);
            SphereConfig.SATELLITES_MOD_ORE_WEIGHT.set(oldWeight);
        }
        helper.succeed();
    }

    record Fixture(ServerLevel level, LevelStorageSource.LevelStorageAccess storage) implements AutoCloseable {
        @Override public void close() throws Exception {
            // Chunk generation queues POI updates on the server. Drain them before closing their storage.
            try {
                // managedBlock also runs fresh TickTasks when this test has exceeded the tick budget.
                level.getServer().managedBlock(() -> !level.getServer().pollTask());
                level.close();
                IOUtilities.waitUntilIOWorkerComplete();
            }
            finally { storage.close(); }
        }
    }
    static Fixture open(GameTestHelper helper, Path root, String name, boolean fresh, boolean classic) throws Exception {
        var server = helper.getLevel().getServer();
        var registry = helper.getLevel().registryAccess();
        var storage = LevelStorageSource.createDefault(root).createAccess(name);
        var settings = registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(classic
                ? ResourceKey.create(Registries.NOISE_SETTINGS, BiosphereWorlds.prefix("biospheres")) : NoiseGeneratorSettings.OVERWORLD);
        var parameters = registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
        var data = server.getWorldData().overworldData();
        boolean initialized = data.isInitialized();
        try {
            data.setInitialized(!fresh);
            var level = new ServerLevel(server, Util.backgroundExecutor(), storage, data, Level.OVERWORLD,
                    new LevelStem(helper.getLevel().dimensionTypeRegistration(), generator), new ChunkProgressListener() {
                        public void updateSpawnPos(ChunkPos pos) { }
                        public void onStatusChange(ChunkPos pos, ChunkStatus status) { }
                        public void start() { }
                        public void stop() { }
                    }, false, 81723, List.of(), false, null);
            return new Fixture(level, storage);
        } catch (Throwable failure) {
            storage.close();
            throw failure;
        } finally { data.setInitialized(initialized); }
    }

    private record Image(List<BlockState> blocks, List<Holder<Biome>> biomes, List<Integer> heights) { }
    private static Image generate(GameTestHelper helper, ServerLevel level) {
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var registry = level.registryAccess();
        var random = level.getChunkSource().randomState();
        var chunk = new ProtoChunk(new ChunkPos(-8, 0), UpgradeData.EMPTY, level, registry.registryOrThrow(Registries.BIOME), null);
        var biomes = generator.createBiomes(random, Blender.empty(), level.structureManager(), chunk);
        biomes.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
        helper.getLevel().getServer().managedBlock(() -> { level.getChunkSource().pollTask(); return biomes.isDone(); });
        biomes.join();
        // El pipeline normal avanza el estado despues del future; aqui lo invocamos por etapas.
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        var terrain = generator.fillFromNoise(Blender.empty(), random, level.structureManager(), chunk);
        terrain.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
        helper.getLevel().getServer().managedBlock(() -> { level.getChunkSource().pollTask(); return terrain.isDone(); });
        terrain.join();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        var noise = chunk.getOrCreateNoiseChunk(unused -> { throw new AssertionError("Generator did not initialize NoiseChunk"); });
        var bound = BiosphereWorldgen.classicWorld(generator);
        helper.assertTrue(BiosphereWorldgen.classicWorld(chunk) == bound && BiosphereWorldgen.classicWorld(noise) == bound,
                "Recipe was not propagated to chunk and aquifer owner");
        var context = new CarvingContext(generator, registry, chunk.getHeightAccessorForGeneration(), noise, random,
                generator.generatorSettings().value().surfaceRule());
        var mask = chunk.getOrCreateCarvingMask(GenerationStep.Carving.AIR);
        for (var key : List.of(ModCarvers.BIOSPHERE_CARVER_KEY, ModCarvers.BIOSPHERE_BRIDGE_CARVER_KEY)) {
            registry.registryOrThrow(Registries.CONFIGURED_CARVER).getHolderOrThrow(key).value().carve(context, chunk,
                    pos -> generator.getBiomeSource().getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2, random.sampler()),
                    RandomSource.create(81723), noise.aquifer(), chunk.getPos(), mask);
        }
        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        var promoted = new net.minecraft.world.level.chunk.LevelChunk(level, chunk, null);
        helper.assertTrue(BiosphereWorldgen.classicWorld(promoted) == bound, "Promotion lost the owning world's recipe");
        if (bound != null) {
            var tag = net.minecraft.world.level.chunk.storage.ChunkSerializer.write(level, chunk);
            // Obliga a la ruta de lectura a reconstruir mapas antes de devolver el ProtoChunk.
            tag.remove("Heightmaps");
            var loaded = net.minecraft.world.level.chunk.storage.ChunkSerializer.read(level, level.getChunkSource().getPoiManager(),
                    new net.minecraft.world.level.chunk.storage.RegionStorageInfo("classic-p7", Level.OVERWORLD, "chunk"), chunk.getPos(), tag);
            helper.assertTrue(BiosphereWorldgen.classicWorld(loaded) == bound && BiosphereWorldgen.isBiosphereChunk(loaded),
                    "Incomplete chunk lost its recipe when decoded");
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                helper.assertTrue(loaded.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) == chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z),
                        "Deserialization rebuilt heightmaps without the saved recipe");
            }
        }
        var blocks = new ArrayList<BlockState>();
        var biomeValues = new ArrayList<Holder<Biome>>();
        var heights = new ArrayList<Integer>();
        for (int x = -128; x < -112; x++) for (int z = 0; z < 16; z++) {
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) blocks.add(chunk.getBlockState(new BlockPos(x, y, z)));
            for (int y = -16; y < 80; y++) biomeValues.add(chunk.getNoiseBiome(x >> 2, y, z >> 2));
            heights.add(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15));
            heights.add(chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15));
        }
        return new Image(blocks, biomeValues, heights);
    }
    private static void assertImage(GameTestHelper helper, Image expected, Image actual, String message) {
        helper.assertTrue(expected.blocks().equals(actual.blocks()), message + " (blocks)");
        helper.assertTrue(expected.biomes().equals(actual.biomes()), message + " (biomes)");
        helper.assertTrue(expected.heights().equals(actual.heights()), message + " (heightmaps)");
    }
}
