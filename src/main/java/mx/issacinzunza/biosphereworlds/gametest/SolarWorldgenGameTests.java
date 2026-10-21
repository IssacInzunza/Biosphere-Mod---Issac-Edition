package mx.issacinzunza.biosphereworlds.gametest;

import com.mojang.serialization.JsonOps;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.config.SolarConfig;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.solar.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Engine tests are opt-in through the project's existing GameTest run configuration. */
@GameTestHolder(BiosphereWorlds.MODID)
@PrefixGameTestTemplate(false)
public final class SolarWorldgenGameTests {
    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void radiusOneReproducesEmptySpawnColumn(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(ResourceKey.create(
                Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("biosphereworlds", "biospheres")));
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var defaults = SolarSettings.defaults();
        int region = SolarLayout.requiredSingleOrbitSystemRadius(1, 1, 180, 400, 75, 12, 16);
        var geometry = new SolarLayout.Params(region, 1500, 1, 1, 1, 180, 400, 400, false, 30, 40, 30, 0, 30, 55, 16);
        var general = new SolarSettings.General(62, defaults.general().shellBlocks(), SphereConfig.BedrockMode.FLOOR,
                defaults.general().satellites());
        var settings = new SolarSettings(geometry, true, SolarLayout.Climate.HOT,
                new SolarSettings.Guardians(false, true, true, 48, 3, 6, 400), defaults.environment(), general, 6);
        for (long seed : new long[]{0, -1, Long.MIN_VALUE, -4634224182739581737L}) {
            var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, settings);
            var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), seed);
            generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
            var planet = generator.layout().systemAtCell(0, 0).bodies().get(1);
            helper.assertTrue(planet.radius() == 1 && planet.climate() == SolarLayout.Climate.TEMPERATE,
                    "Fixture must retain the configured radius and select the spawn planet");
            int x = (int) Math.floor(planet.x());
            int z = (int) Math.floor(planet.z());
            var column = generator.getBaseColumn(x, z, level, random);
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                helper.assertTrue(column.getBlock(y).isAir(), "Radius one has no terrain interior");
            }
            int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
            helper.assertTrue(y == level.getMinBuildHeight(), "Reproduce the invalid spawn height before validation");
            assertSpawnValidation(helper, planet, new BlockPos(x, y, z), false);
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void initialSpawnRequiresSupportAndStandingSpace(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 4, 2));
        var planet = new SolarLayout.Body(1, pos.getX(), pos.getY(), pos.getZ(), 128, false, SolarLayout.Climate.TEMPERATE);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        assertSpawnValidation(helper, planet, pos, true);
        level.setBlock(pos.below(), Blocks.WATER.defaultBlockState(), 2);
        assertSpawnValidation(helper, planet, pos, true);
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
        assertSpawnValidation(helper, planet, pos, false);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        for (var obstruction : new BlockPos[]{pos, pos.above()}) {
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 2);
            assertSpawnValidation(helper, planet, pos, false);
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 2);
        assertSpawnValidation(helper, planet, pos, false);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        // A nominally positive radius is insufficient; the entire standing column must fit.
        var small = new SolarLayout.Body(1, pos.getX(), pos.getY(), pos.getZ(), 2, false, SolarLayout.Climate.TEMPERATE);
        assertSpawnValidation(helper, small, pos, false);
        assertSpawnValidation(helper, planet, new BlockPos(pos.getX(), level.getMinBuildHeight(), pos.getZ()), false);
        assertSpawnValidation(helper, planet, new BlockPos(pos.getX(), level.getMaxBuildHeight() - 1, pos.getZ()), false);
        helper.succeed();
    }

    private static void assertSpawnValidation(GameTestHelper helper, SolarLayout.Body planet, BlockPos pos, boolean valid)
            throws ReflectiveOperationException {
        var validate = mx.issacinzunza.biosphereworlds.event.SolarEvents.class.getDeclaredMethod("validateSpawn",
                net.minecraft.server.level.ServerLevel.class, SolarLayout.Body.class, BlockPos.class);
        validate.setAccessible(true);
        boolean accepted = true;
        try {
            validate.invoke(null, helper.getLevel(), planet, pos);
        } catch (java.lang.reflect.InvocationTargetException error) {
            if (!(error.getCause() instanceof IllegalStateException rejection)) throw error;
            helper.assertTrue(rejection.getMessage().startsWith("Solar initial spawn is not viable at "),
                    "Spawn rejection must explain the failure instead of masking another exception");
            accepted = false;
        }
        helper.assertTrue(accepted == valid, "Unexpected spawn validation result at " + pos + ", radius=" + planet.radius());
    }

    /** Produces independent orbit envelopes for current test fixtures; older snapshots keep their exact params. */
    private static SolarLayout.Params geometryForVersion(SolarLayout.Params p, int version) {
        if (version < 6) return p;
        return new SolarLayout.Params(SolarLayout.requiredSingleOrbitSystemRadius(p.planetCount(), p.maxRadius(),
                p.centralRadius(), p.minGap(), 75, 12, p.satelliteGap()), p.systemGap(), p.planetCount(),
                p.minRadius(), p.maxRadius(), p.centralRadius(), p.minGap(), p.maxGap(), p.coldInside(),
                p.innerPercent(), p.middlePercent(), p.outerPercent(), p.satelliteCount(),
                p.satelliteMinElevation(), p.satelliteMaxElevation(), p.satelliteGap());
    }

    @GameTest(template = "solar_empty")
    public static void centralBedrockCeilingIsSharedByGenerationAndHeightmaps(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var defaults = SolarSettings.defaults();
        var geometry = geometryForVersion(defaults.geometry(), 6);
        var layout = new SolarLayout(19, geometry, 6, defaults.general().placement());
        var sun = layout.systemAtCell(0, 0).central();
        for (boolean enabled : new boolean[]{false, true}) {
            for (int y : new int[]{63, 64, 80, 127}) {
                BlockPos position = null;
                for (int offset = 0; offset <= sun.radius(); offset++) {
                    var candidate = BlockPos.containing(sun.x() + offset, y, sun.z());
                    if (sun.contains(candidate.getX(), y, candidate.getZ(), 2)
                            && !sun.contains(candidate.getX(), y, candidate.getZ(), 5)) {
                        position = candidate;
                        break;
                    }
                }
                helper.assertTrue(position != null, "Fixture must lie in the previous central bedrock band");
                var chunk = new ProtoChunk(new ChunkPos(position), UpgradeData.EMPTY, level, registry, null);
                ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(layout);
                var settings = new SolarSettings(geometry, true, SolarLayout.Climate.HOT, defaults.guardians(),
                        new SolarSettings.Environment(false, 20, 20, enabled), defaults.general(), 6);
                chunk.setBlockState(position, Blocks.STONE.defaultBlockState(), false);
                new SolarVolumePainter(layout, settings, java.util.List.of(), 63).paint(chunk, SolarVolumePainter.Pass.SHELL);
                boolean expectedBedrock = enabled && y <= 63;
                helper.assertTrue(chunk.getBlockState(position).is(expectedBedrock ? Blocks.BEDROCK : Blocks.STONE),
                        "Central liner must stop at Y=63 and preserve the original terrain above it");
                var filtered = SolarChunkGenerator.heightmapState(chunk, position, chunk.getBlockState(position));
                helper.assertTrue(expectedBedrock ? filtered.isAir() : filtered.is(Blocks.STONE),
                        "Heightmap filtering must use the same liner ceiling as generation");
                if (y > 63) {
                    helper.assertTrue(chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
                            .getFirstAvailable(position.getX() & 15, position.getZ() & 15) == y + 1,
                            "Terrain above the liner ceiling must remain visible to heightmaps");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void volumeRefactorMatchesLiteralBlocksAndHeightmaps(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var defaults = SolarSettings.defaults();
        long previousNanos = 0;
        long currentNanos = 0;
        int comparisons = 0;
        for (int version : new int[]{1, 2, 4, 5, 6}) {
            for (boolean cold : new boolean[]{false, true}) {
                var general = new SolarSettings.General(64,
                        java.util.List.of("minecraft:blue_stained_glass", "invalid id", "minecraft:water"),
                        cold ? SphereConfig.BedrockMode.NONE : SphereConfig.BedrockMode.FLOOR,
                        defaults.general().satellites(), cold);
                var settings = new SolarSettings(geometryForVersion(defaults.geometry(), version), true,
                        cold ? SolarLayout.Climate.COLD : SolarLayout.Climate.HOT,
                        defaults.guardians(), new SolarSettings.Environment(true, 20, 20, cold), general, version);
                var layout = new SolarLayout(71, settings.geometry(), version, general.placement());
                var system = layout.systemAtCell(0, 0);
                var center = system.central();
                var planet = system.bodies().get(1);
                var locations = new java.util.ArrayList<ChunkPos>();
                locations.add(new ChunkPos(BlockPos.containing(center.x(), center.y(), center.z())));
                locations.add(new ChunkPos(BlockPos.containing(center.x() + center.radius() - 1, center.y(), center.z())));
                locations.add(new ChunkPos(BlockPos.containing(planet.x(), planet.y(), planet.z())));
                locations.add(new ChunkPos(BlockPos.containing(planet.x() + planet.radius() - 1, planet.y(), planet.z())));
                if (!system.moons().isEmpty()) {
                    var moon = system.moons().getFirst().sphere();
                    locations.add(new ChunkPos(BlockPos.containing(moon.centerX(), moon.centerY(), moon.centerZ())));
                }
                locations.add(new ChunkPos(200, 200));
                var baseline = new SolarVolumeBaseline(layout, settings,
                        mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.VANILLA_MINERALS, 63);
                var painter = new SolarVolumePainter(layout, settings,
                        mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.VANILLA_MINERALS, 63);
                for (var location : locations) {
                    var oldChunk = new ProtoChunk(location, UpgradeData.EMPTY, level, registry, null);
                    var newChunk = new ProtoChunk(location, UpgradeData.EMPTY, level, registry, null);
                    ((SolarChunkAccess) oldChunk).biosphereworlds$solarLayout(layout);
                    ((SolarChunkAccess) newChunk).biosphereworlds$solarLayout(layout);
                    var cursor = new BlockPos.MutableBlockPos();
                    for (int x = location.getMinBlockX(); x <= location.getMaxBlockX(); x++) {
                        for (int z = location.getMinBlockZ(); z <= location.getMaxBlockZ(); z++) {
                            for (int y = level.getMinBuildHeight(); y <= 72; y++) {
                                var state = y == level.getMinBuildHeight() ? Blocks.BEDROCK.defaultBlockState()
                                        : y <= 57 ? Blocks.STONE.defaultBlockState()
                                        : y <= 63 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                                cursor.set(x, y, z);
                                oldChunk.setBlockState(cursor, state, false);
                                newChunk.setBlockState(cursor, state, false);
                            }
                        }
                    }
                    // An existing block above every sphere must still be cleared by the bounded pass.
                    if (location.equals(locations.get(1))) {
                        cursor.set(location.getMinBlockX(), level.getMaxBuildHeight() - 1, location.getMinBlockZ());
                        oldChunk.setBlockState(cursor, Blocks.STONE.defaultBlockState(), false);
                        newChunk.setBlockState(cursor, Blocks.STONE.defaultBlockState(), false);
                    }
                    for (var pass : new SolarVolumePainter.Pass[]{SolarVolumePainter.Pass.NOISE,
                            SolarVolumePainter.Pass.SURFACE, SolarVolumePainter.Pass.SHELL, SolarVolumePainter.Pass.SHELL}) {
                        long start;
                        // Alternate order to reduce warmup/order bias; timings are diagnostics, not assertions.
                        if ((comparisons & 1) == 0) {
                            start = System.nanoTime();
                            baseline.finishVolume(oldChunk, pass == SolarVolumePainter.Pass.SHELL, pass == SolarVolumePainter.Pass.SURFACE);
                            previousNanos += System.nanoTime() - start;
                            start = System.nanoTime();
                            painter.paint(newChunk, pass);
                            currentNanos += System.nanoTime() - start;
                        } else {
                            start = System.nanoTime();
                            painter.paint(newChunk, pass);
                            currentNanos += System.nanoTime() - start;
                            start = System.nanoTime();
                            baseline.finishVolume(oldChunk, pass == SolarVolumePainter.Pass.SHELL, pass == SolarVolumePainter.Pass.SURFACE);
                            previousNanos += System.nanoTime() - start;
                        }
                        for (int x = location.getMinBlockX(); x <= location.getMaxBlockX(); x++) {
                            for (int z = location.getMinBlockZ(); z <= location.getMaxBlockZ(); z++) {
                                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                                    cursor.set(x, y, z);
                                    if (!oldChunk.getBlockState(cursor).equals(newChunk.getBlockState(cursor))) {
                                        helper.fail("Volume mismatch at " + cursor + " in layout " + version + " / " + pass);
                                    }
                                }
                            }
                        }
                        for (var type : Heightmap.Types.values()) {
                            helper.assertTrue(java.util.Arrays.equals(oldChunk.getOrCreateHeightmapUnprimed(type).getRawData(),
                                    newChunk.getOrCreateHeightmapUnprimed(type).getRawData()), "Heightmap mismatch: " + type);
                        }
                        comparisons++;
                    }
                }
            }
        }
        BiosphereWorlds.LOGGER.info("Solar volume equivalence: {} passes; literal={}ms refactored={}ms (diagnostic only)",
                comparisons, previousNanos / 1_000_000, currentNanos / 1_000_000);
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void solarReusesBiospheresDensityWithoutChangingClassicState(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(ResourceKey.create(
                Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("biosphereworlds", "biospheres")));
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var template = noise.value();
        var rawRouter = template.noiseRouter().mapAll(function -> {
            if (function instanceof mx.issacinzunza.biosphereworlds.density.MultipleSpheresDistanceFunction) return net.minecraft.world.level.levelgen.DensityFunctions.zero();
            if (function instanceof mx.issacinzunza.biosphereworlds.density.SphereEdgeOceanFunction ocean) return ocean.input();
            return function;
        });
        var rawSettings = new NoiseGeneratorSettings(template.noiseSettings(), template.defaultBlock(), template.defaultFluid(), rawRouter,
                template.surfaceRule(), template.spawnTarget(), template.seaLevel(), template.disableMobGeneration(),
                template.isAquifersEnabled(), template.oreVeinsEnabled(), template.useLegacyRandomSource());
        var runtimeField = SolarChunkGenerator.class.getDeclaredMethod("terrainRandom", RandomState.class);
        runtimeField.setAccessible(true);
        var finish = SolarChunkGenerator.class.getDeclaredMethod("paintVolume", net.minecraft.world.level.chunk.ChunkAccess.class, mx.issacinzunza.biosphereworlds.worldgen.solar.SolarVolumePainter.Pass.class);
        finish.setAccessible(true);
        var defaults = SolarSettings.defaults();
        for (long seed : new long[]{0, 81723}) {
            var original = RandomState.create(template, registries.lookupOrThrow(Registries.NOISE), seed);
            var raw = RandomState.create(rawSettings, registries.lookupOrThrow(Registries.NOISE), seed);
            var sample = new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(250, 64, 250);
            long before = Double.doubleToRawLongBits(original.router().finalDensity().compute(sample));
            for (int version : new int[]{5, 6}) for (boolean enabled : new boolean[]{false, true}) {
                var environment = new SolarSettings.Environment(enabled, 20, 20, false);
                var settings = new SolarSettings(geometryForVersion(defaults.geometry(), version), true, SolarLayout.Climate.HOT,
                        defaults.guardians(), environment, defaults.general(), version);
                var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, settings);
                generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), original, seed);
                var bound = (RandomState) runtimeField.invoke(generator, original);
                helper.assertTrue(bound != original && !BiosphereWorldgen.isBiosphereGenerator(generator)
                        && !BiosphereWorldgen.isBiosphereBiomeSource(generator.getBiomeSource()), "Solar must own its state without activating classic markers");
                var body = generator.layout().systemAtCell(0, 0).bodies().get(1);
                for (int offset = 0; offset < body.radius(); offset += 3) for (int y = 32; y <= 96; y += 8) {
                    int x = (int) body.x() + offset;
                    int z = (int) body.z();
                    if (!body.contains(x, y, z, 2)) continue;
                    var context = new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z);
                    double weight = enabled ? mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean.weight(body.radius(),
                            x-body.x(), y-body.y(), z-body.z(), .2) : 0;
                    var oldRouter = raw.router();
                    var solarRouter = bound.router();
                    for (int mode = 0; mode < 3; mode++) {
                        double input = (mode == 0 ? oldRouter.finalDensity() : mode == 1 ? oldRouter.initialDensityWithoutJaggedness() : oldRouter.continents()).compute(context);
                        double expected = mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean.transformWeighted(
                                mode == 2 ? mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean.Mode.CLIMATE : mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean.Mode.FLOOR,
                                20, y, input, weight);
                        double actual = (mode == 0 ? solarRouter.finalDensity() : mode == 1 ? solarRouter.initialDensityWithoutJaggedness() : solarRouter.continents()).compute(context);
                        helper.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected), "Solar must use the shared seeded density profile, including its smooth transition");
                    }
                }
                int x = (int) (body.x() + body.radius() * .9);
                int z = (int) body.z();
                var pos = new BlockPos(x, 80, z);
                var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level, registries.registryOrThrow(Registries.BIOME), null);
                ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(generator.layout());
                chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                finish.invoke(generator, chunk, SolarVolumePainter.Pass.NOISE);
                helper.assertTrue(chunk.getBlockState(pos).is(Blocks.STONE), "Solar must not carve a hard ocean band after generating the density terrain");
                var column = generator.getBaseColumn((int) body.x(), (int) body.z(), level, original);
                boolean terrain = false;
                for (int y = -60; y < 80; y++) terrain |= !column.getBlock(y).isAir();
                helper.assertTrue(terrain, "The adapted router must generate actual terrain and fluids");
                var center = generator.layout().systemAtCell(0, 0).central();
                var stronghold = registries.registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(
                        Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("stronghold")));
                var start = stronghold.value().generate(registries, generator, generator.getBiomeSource(), bound,
                        level.getStructureManager(), seed, new ChunkPos(new BlockPos((int) center.x(), 32, (int) center.z())), 0,
                        level, stronghold.value().biomes()::contains);
                helper.assertTrue(start.isValid() && start.getPieces().stream().filter(piece -> piece instanceof StrongholdPieces.PortalRoom).count() == 1,
                        "The shared terrain router must preserve the central Stronghold and its unique portal room");
                var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
                var saved = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow();
                var restored = (SolarChunkGenerator) ChunkGenerator.CODEC.parse(ops, saved).getOrThrow();
                helper.assertTrue(restored.solarSettings().equals(settings) && restored.generatorSettings().equals(noise),
                        "Only the template and Solar snapshot must be serialized, never runtime density adapters");
            }
            helper.assertTrue(before == Double.doubleToRawLongBits(original.router().finalDensity().compute(sample)), "Binding Solar must not mutate the classic RandomState");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void orbitalCagesAreGeneratedOnlyWhenEnabled(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var defaults = SolarSettings.defaults();
        for (int version : new int[]{4, 5, 6}) for (boolean enabled : new boolean[]{false, true}) {
            var guardians = new SolarSettings.Guardians(enabled, true, true, 48, 2, 3, 600);
            var settings = new SolarSettings(geometryForVersion(defaults.geometry(), version), true, SolarLayout.Climate.HOT, guardians, defaults.environment(), defaults.general(), version);
            var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, settings);
            var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), 19);
            generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, 19);
            var posts = generator.layout().guardianPosts(generator.layout().systemAtCell(0, 0));
            helper.assertTrue(posts.size() == (version == 4 ? 3 : 12), "Current cages must cover all sides; legacy layouts retain three sites");
            for (var post : posts) {
                var pos = new BlockPos(post.x(), post.y(), post.z());
                var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level, registries.registryOrThrow(Registries.BIOME), null);
                ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(generator.layout());
                helper.assertTrue(generator.layout().bodyAt(post.x(), post.y(), post.z(), 0) == null, "Cages must be outside the sun");
                SolarGuardians.placeCages(chunk, generator);
                if (enabled && version >= 5) {
                    var block = (net.minecraft.world.level.block.entity.SpawnerBlockEntity) chunk.getBlockEntity(pos);
                    var tag = block.getSpawner().save(new net.minecraft.nbt.CompoundTag());
                    helper.assertTrue(tag.getShort("RequiredPlayerRange") == SolarLayout.GUARDIAN_ACTIVATION_RANGE
                            && tag.getShort("SpawnCount") == 2 && tag.getShort("MinSpawnDelay") == SolarGuardians.MIN_DELAY
                            && tag.getShort("MaxSpawnDelay") == SolarGuardians.MAX_DELAY, "Current cage policy must be consistent");
                    helper.assertTrue(tag.getCompound("SpawnData").getCompound("entity").size() == 1,
                            "Entity data must retain vanilla finalizeSpawn initialization");
                }
                helper.assertTrue(chunk.getBlockState(pos).is(enabled ? Blocks.SPAWNER : Blocks.AIR), "Disabled cages must not write a block");
                helper.assertTrue((chunk.getBlockEntity(pos) != null) == enabled, "Disabled cages must not register a block entity");
                helper.assertTrue(chunk.getBlockState(pos.below()).is(enabled ? Blocks.OBSIDIAN : Blocks.AIR), "Disabled cages must not create a pedestal");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    @SuppressWarnings("removal")
    public static void guardianCagesSpawnAgainAfterDistantKills(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 12, 1));
        var player = helper.makeMockServerPlayerInLevel();
        player.moveTo(pos.getX() + 80.5, pos.getY(), pos.getZ() + .5, 0, 0);
        var area = new net.minecraft.world.phys.AABB(pos).inflate(32);
        try {
            for (int index = 0; index < 3; index++) {
                var block = SolarGuardians.createSpawner(pos, index, false);
                level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
                level.setBlockEntity(block);
                var saved = block.saveWithFullMetadata(level.registryAccess());
                var restored = new net.minecraft.world.level.block.entity.SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
                restored.loadWithComponents(saved, level.registryAccess());
                helper.assertTrue(restored.getPersistentData().getBoolean("biosphereworlds_solar_guardian"), "Cage ownership must survive saving");
                helper.assertTrue(saved.getShort("RequiredPlayerRange") == 96 && saved.getShort("SpawnCount") == 1
                        && saved.getShort("MinSpawnDelay") == 600 && saved.getShort("MaxSpawnDelay") == 1200, "Guardian cages must have restrained rates and ranged activation");
                var type = switch (index) { case 0 -> net.minecraft.world.entity.EntityType.GHAST; case 1 -> net.minecraft.world.entity.EntityType.PHANTOM; default -> net.minecraft.world.entity.EntityType.BLAZE; };
                for (int round = 0; round < 2; round++) {
                    var tag = block.getSpawner().save(new net.minecraft.nbt.CompoundTag());
                    tag.putShort("Delay", (short) 0);
                    block.getSpawner().load(level, pos, tag);
                    for (int attempt = 0; attempt < 32; attempt++) block.getSpawner().serverTick(level, pos);
                    var mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area, mob -> mob.getType() == type);
                    helper.assertTrue(mobs.size() == 1, "The cage must spawn exactly one " + type + " with a player 80 blocks away, including after a kill");
                    helper.assertTrue(mobs.getFirst().getPersistentData().getBoolean("biosphereworlds_solar_guardian"), "Spawned guardians must carry the explosion-protection marker");
                    mobs.forEach(net.minecraft.world.entity.Entity::discard);
                }
            }
        } finally {
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area, mob -> mob.getPersistentData().getBoolean("biosphereworlds_solar_guardian")).forEach(net.minecraft.world.entity.Entity::discard);
            level.removeBlock(pos, false);
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    @SuppressWarnings("removal")
    public static void modernCagesInitializeAndRespawnEveryGuardianAtRange(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 12, 1));
        var player = helper.makeMockServerPlayerInLevel();
        player.moveTo(pos.getX() + 132.5, pos.getY(), pos.getZ() + .5, 0, 0);
        var area = new net.minecraft.world.phys.AABB(pos).inflate(160);
        var forced = new java.util.ArrayList<ChunkPos>();
        var playerChunk = new ChunkPos(player.blockPosition());
        for (int x = playerChunk.x - 2; x <= playerChunk.x + 2; x++) {
            for (int z = playerChunk.z - 2; z <= playerChunk.z + 2; z++) {
                if (level.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
            }
        }
        // Real players already have an active neighborhood. Let this mock player's
        // newly loaded entity sections become visible before querying spawned mobs.
        helper.runAfterDelay(20, () -> {
            // The test server uses a flat generator. Exercise the two module hooks directly
            // through real engine events, restricted to this test's owned cage.
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck> positioning = event -> {
                if (event.getSpawner() != null && event.getSpawner().getOwner() != null
                        && event.getSpawner().getOwner().left().filter(block -> block.getBlockPos().equals(pos)).isPresent()) {
                    SolarGuardians.adjustSpawn(event);
                }
            };
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent> marking = event -> {
                if (event.getSpawner() != null && event.getSpawner().left().filter(block -> block.getBlockPos().equals(pos)).isPresent()) {
                    SolarGuardians.markSpawn(event);
                }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(positioning);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(marking);
            try {
                for (var type : java.util.List.of(net.minecraft.world.entity.EntityType.GHAST,
                        net.minecraft.world.entity.EntityType.PHANTOM, net.minecraft.world.entity.EntityType.BLAZE)) {
                    var block = SolarGuardians.createSpawner(pos, 0, true);
                    level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
                    level.setBlockEntity(block);
                    for (int round = 0; round < 2; round++) {
                        var tag = block.getSpawner().save(new net.minecraft.nbt.CompoundTag());
                        tag.putShort("Delay", (short) 0);
                        tag.getCompound("SpawnData").getCompound("entity").putString("id",
                                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                        block.getSpawner().load(level, pos, tag);
                        for (int attempt = 0; attempt < 32; attempt++) block.getSpawner().serverTick(level, pos);
                        var mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area, mob -> mob.getType() == type);
                        helper.assertTrue(mobs.size() >= 1 && mobs.size() <= 2, "Modern cage must attempt a pair of " + type + " at 132 blocks after each kill, respecting collisions");
                        var mob = mobs.getFirst();
                        helper.assertTrue(mob.getPersistentData().getBoolean(SolarGuardians.MARKER), "Finalization must mark the initialized guardian");
                        double engagementRange = type == net.minecraft.world.entity.EntityType.PHANTOM ? 12 : 16;
                        helper.assertTrue(mob.distanceToSqr(player) <= engagementRange * engagementRange + .001,
                                "Coverage-edge spawn must be within the guardian's engagement range");
                        mobs.forEach(net.minecraft.world.entity.Entity::discard);
                    }
                }
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(positioning);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(marking);
                level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area,
                        mob -> mob.getPersistentData().getBoolean(SolarGuardians.MARKER)).forEach(net.minecraft.world.entity.Entity::discard);
                level.removeBlock(pos, false);
                level.getServer().getPlayerList().remove(player);
                forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            }
            helper.succeed();
        });
    }

    @GameTest(template = "solar_empty")
    public static void savedGuardianCagesUpgradeWithoutReplacingBlocks(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 12, 1));
        var upgrade = SolarGuardians.class.getDeclaredMethod("upgradeCage", net.minecraft.server.level.ServerLevel.class,
                BlockPos.class, net.minecraft.world.level.BaseSpawner.class);
        upgrade.setAccessible(true);
        for (boolean marked : new boolean[]{false, true}) {
            var block = SolarGuardians.createSpawner(pos, 0, false);
            if (!marked) block.getPersistentData().remove(SolarGuardians.MARKER);
            var before = block.getSpawner().save(new net.minecraft.nbt.CompoundTag());
            upgrade.invoke(null, level, pos, block.getSpawner());
            var after = block.getSpawner().save(new net.minecraft.nbt.CompoundTag());
            if (!marked) helper.assertTrue(before.equals(after), "Ordinary spawners must remain identical");
            else {
                helper.assertTrue(after.getShort("RequiredPlayerRange") == SolarLayout.GUARDIAN_ACTIVATION_RANGE
                        && after.getShort("SpawnCount") == 2 && after.getShort("MinSpawnDelay") == 160
                        && after.getShort("MaxSpawnDelay") == 240 && after.getShort("MaxNearbyEntities") == 8,
                        "Saved Solar cages must adopt the new policy");
                helper.assertTrue(after.getCompound("SpawnData").getCompound("entity").size() == 1,
                        "Migration must retain vanilla mob initialization");
                upgrade.invoke(null, level, pos, block.getSpawner());
                helper.assertTrue(after.equals(block.getSpawner().save(new net.minecraft.nbt.CompoundTag())),
                        "Migration must be idempotent");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void disabledSatellitesSkipEmptyMineralValidation(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var defaults = SolarSettings.defaults();
        var p = defaults.geometry();
        var allBlocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).toList();
        var options = defaults.general().satellites();
        // Empty, nonempty with zero weight, and valid with a missing id to exercise Solar diagnostics.
        for (int catalogCase = 0; catalogCase < 3; catalogCase++) {
            var excluded = new SolarSettings.Satellites(options.minSize(), options.maxSize(), options.minGapPercent(),
                    options.maxGapPercent(), catalogCase == 0 ? java.util.List.of() : java.util.List.of("minecraft:stone", "missingmod:ore"),
                    catalogCase == 0 ? allBlocks : allBlocks.stream().filter(id -> !id.equals("minecraft:stone")).toList(),
                    catalogCase == 1 ? 0 : options.modWeight(), options.modDensity());
            var resolved = mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog.resolve(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK, excluded.included(), excluded.excluded(),
                    excluded.modWeight(), excluded.modDensity());
            helper.assertTrue(catalogCase == 0 ? resolved.candidates().isEmpty()
                    : resolved.candidates().equals(java.util.List.of(new mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.Mineral(
                            "minecraft:stone", excluded.modWeight(), options.modDensity()))), "Catalog fixture differs from its intended weights");
            if (catalogCase > 0) helper.assertTrue(resolved.ignored().stream().anyMatch(entry -> entry.startsWith("missingmod:ore")),
                    "Missing Solar entries must retain a diagnostic");
            for (int count : new int[]{0, 3}) {
                var geometry = new SolarLayout.Params(p.systemRadius(), p.systemGap(), p.planetCount(), p.minRadius(), p.maxRadius(),
                        p.centralRadius(), p.minGap(), p.maxGap(), p.coldInside(), p.innerPercent(), p.middlePercent(),
                        p.outerPercent(), count, p.satelliteMinElevation(), p.satelliteMaxElevation(), p.satelliteGap());
                var general = new SolarSettings.General(64, defaults.general().shellBlocks(), SphereConfig.BedrockMode.FLOOR, excluded);
                var settings = new SolarSettings(geometryForVersion(geometry, 6), true, SolarLayout.Climate.HOT,
                        defaults.guardians(), defaults.environment(), general, 6);
                var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, settings);
                var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), 4);
                boolean failed = false;
                try {
                    generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, 4);
                } catch (IllegalArgumentException expected) {
                    helper.assertTrue(expected.getMessage().contains("positive-weight candidate"), "Catalog failure must explain the settings to review");
                    failed = true;
                }
                helper.assertTrue(failed == (count > 0 && catalogCase < 2),
                        "Only enabled satellites with empty/zero-weight catalogs must fail before painting");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void configurationMigrationPreservesExistingChoices(GameTestHelper helper) throws java.io.IOException {
        var folder = java.nio.file.Path.of("solar-config-migration-" + java.util.UUID.randomUUID());
        java.nio.file.Files.createDirectories(folder);
        String original = "[Settings]\nradius = 256\n[SolarOrbits]\nclimate_direction = 1\n";
        java.nio.file.Files.writeString(folder.resolve("BiosphereWorlds.toml"), original);
        SphereConfig.migrateConfigFiles(folder);
        var target = folder.resolve("biosphereworlds/SolarSystems.toml");
        helper.assertTrue(java.nio.file.Files.readString(target).equals(original), "Migration must preserve current unified settings");
        java.nio.file.Files.writeString(target, "custom solar choices");
        SphereConfig.migrateConfigFiles(folder);
        helper.assertTrue(java.nio.file.Files.readString(target).equals("custom solar choices"), "Migration must never overwrite an existing Solar file");
        helper.assertTrue(java.nio.file.Files.readString(folder.resolve("BiosphereWorlds.toml")).equals(original), "Migration must retain the old file");
        var legacy = folder.resolve("legacy");
        java.nio.file.Files.createDirectories(legacy);
        java.nio.file.Files.writeString(legacy.resolve("SolarSystems.toml"), "[SolarGeneral]\nradius = 384\n[SolarOrbits]\nclimate_direction = \"cold_to_hot\"\n");
        SphereConfig.migrateConfigFiles(legacy);
        String converted = java.nio.file.Files.readString(legacy.resolve("biosphereworlds/SolarSystems.toml"));
        helper.assertTrue(converted.contains("[Settings]") && converted.contains("radius = 384") && converted.contains("climate_direction = 1"), "Old independent Solar settings must keep their radius and cold direction");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void oversizedPlanetsRespectSharedTopCap(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var defaults = SolarSettings.defaults();
        var geometry = new SolarLayout.Params(SolarLayout.requiredSystemRadius(1, 512, 180, 400, 75, 12, 16),
                1500, 1, 512, 512, 180, 400, 400, false, 30, 40, 30, 0, 30, 55, 16);
        var finish = SolarChunkGenerator.class.getDeclaredMethod("paintVolume", net.minecraft.world.level.chunk.ChunkAccess.class, mx.issacinzunza.biosphereworlds.worldgen.solar.SolarVolumePainter.Pass.class);
        finish.setAccessible(true);
        for (boolean cap : new boolean[]{false, true}) {
            var general = new SolarSettings.General(62, defaults.general().shellBlocks(), defaults.general().bedrockMode(), defaults.general().satellites(), cap);
            var settings = new SolarSettings(geometry, true, SolarLayout.Climate.HOT, defaults.guardians(), defaults.environment(), general, 3);
            var restored = SolarSettings.CODEC.parse(JsonOps.INSTANCE, SolarSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow()).getOrThrow();
            helper.assertTrue(restored.equals(settings), "The shared top cap and oversized radius must survive saving");
            var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, restored);
            var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), 4);
            generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, 4);
            var body = generator.layout().systemAtCell(0, 0).bodies().get(1);
            helper.assertTrue(body.radius() == 512 && body.y() == 62, "Solar must use the configured radius and center without fitting them to world height");
            var pos = new BlockPos((int) body.x(), 319, (int) body.z());
            var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level, registries.registryOrThrow(Registries.BIOME), null);
            ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(generator.layout());
            chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
            finish.invoke(generator, chunk, SolarVolumePainter.Pass.SHELL);
            helper.assertTrue(chunk.getBlockState(pos).is(cap ? Blocks.GLASS : Blocks.STONE), "Top closure must follow close_open_top exactly");
            helper.assertTrue(SolarChunkGenerator.heightmapState(chunk, pos, chunk.getBlockState(pos)).is(cap ? Blocks.AIR : Blocks.STONE), "Heightmaps must ignore an enabled cap and preserve terrain when disabled");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void solarUsesIndependentConfiguration(GameTestHelper helper) {
        var settings = SolarConfig.snapshot();
        helper.assertTrue(settings.layoutVersion() == 6, "New Solar worlds must save layout 6");
        var general = settings.general();
        helper.assertTrue(SolarConfig.GENERAL.RADIUS != SphereConfig.RADIUS && SolarConfig.GENERAL.SPHERE_BLOCK != SphereConfig.SPHERE_BLOCK, "Solar and Biospheres must hold distinct configuration values");
        helper.assertTrue(general.centerY() == SolarConfig.GENERAL.CENTER_Y.get().intValue(), "Solar must read the independent Solar center Y");
        helper.assertTrue(general.shellBlocks().equals(SolarConfig.GENERAL.SPHERE_BLOCK.get()), "Solar must read the independent Solar shell palette");
        helper.assertTrue(settings.geometry().minRadius() == SolarConfig.GENERAL.MIN_RADIUS.get().intValue()
                && settings.geometry().maxRadius() == SolarConfig.GENERAL.RADIUS.get().intValue(), "Solar must read independent Solar radii");
        helper.assertTrue(general.satellites().excluded().equals(SolarConfig.GENERAL.SATELLITES_EXCLUDED_ORES.get()), "Solar must read independent Solar ore exclusions");
        helper.assertTrue(general.closeOpenTop() == SolarConfig.GENERAL.CLOSE_OPEN_TOP.get(), "Solar must read the independent Solar top cap");
        boolean cold = SolarConfig.OPTIONS.SOLAR_CLIMATE_DIRECTION.get() == 1;
        helper.assertTrue(settings.geometry().coldInside() == cold
                && settings.centralClimate() == (cold ? SolarLayout.Climate.COLD : SolarLayout.Climate.HOT), "Numeric temperature must control both center and orbital direction");
        helper.assertTrue(settings.guardians().enabled() == SolarConfig.OPTIONS.SOLAR_GUARDIANS_ENABLED.get(), "Solar must read the central guardian switch");
        var environment = new SolarSettings.Environment(true, 100, 100, false);
        helper.assertTrue(environment.edgeOceanDepth() == 100, "Solar must accept the independent Solar ocean depth range");
        helper.succeed();
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void centralStrongholdsAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var stronghold = registries.registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(
                Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("stronghold")));
        var defaults = SolarSettings.defaults();
        for (int version : new int[]{2, 6}) for (long seed : new long[]{0, -1, Long.MIN_VALUE, 81723, -4634224182739581737L}) {
            var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(version < 5
                    ? NoiseGeneratorSettings.OVERWORLD : ResourceKey.create(Registries.NOISE_SETTINGS,
                            ResourceLocation.fromNamespaceAndPath("biosphereworlds", "biospheres")));
            var options = new SolarSettings(geometryForVersion(defaults.geometry(), version), defaults.climateEnabled(),
                    defaults.centralClimate(), defaults.guardians(), defaults.environment(), defaults.general(), version);
            var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, options);
            var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), seed);
            var state = generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
            helper.assertTrue(!BiosphereWorldgen.isBiosphereGenerator(generator), "Solar activated the classic generator branch");
            long sets = state.possibleStructureSets().stream().filter(s -> s.value().structures().stream()
                    .anyMatch(e -> e.structure().value() instanceof StrongholdStructure)).count();
            helper.assertTrue(sets == 1, "Expected exactly one stronghold placement set");
            for (int cx = -2; cx <= 2; cx++) {
                var system = generator.layout().systemAtCell(cx, -cx);
                var body = system.central();
                var pos = new ChunkPos(BlockPos.containing(body.x(), body.y(), body.z()));
                var placement = new SolarPlacement(options.geometry());
                helper.assertTrue(placement.isStructureChunk(state, pos.x, pos.z), "Placement differs from central geometry");
                helper.assertTrue(placement.getPotentialStructureChunk(seed, pos.x, pos.z).equals(pos), "Locate candidate differs from generation candidate");
                var start = stronghold.value().generate(registries, generator, generator.getBiomeSource(), random,
                        level.getStructureManager(), seed, pos, 0, level, stronghold.value().biomes()::contains);
                helper.assertTrue(start.isValid(), "Central stronghold rejected: seed=" + seed + " cell=" + cx
                        + " biome=" + generator.getBiomeSource().getNoiseBiome(Math.floorDiv(pos.getMinBlockX(), 4), 0,
                        Math.floorDiv(pos.getMinBlockZ(), 4), random.sampler()).unwrapKey());
                helper.assertTrue(start.getPieces().stream().filter(p -> p instanceof StrongholdPieces.PortalRoom).count() == 1,
                        "Stronghold has no unique portal room");
                var box = net.minecraft.world.level.levelgen.structure.StructurePiece.createBoundingBox(start.getPieces().stream());
                helper.assertTrue(body.containsBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), 3),
                        "A stronghold piece escaped its central sphere");
                var orbital = system.bodies().get(1);
                var outside = stronghold.value().generate(registries, generator, generator.getBiomeSource(), random,
                        level.getStructureManager(), seed, new ChunkPos(BlockPos.containing(orbital.x(), orbital.y(), orbital.z())),
                        0, level, b -> true);
                helper.assertTrue(!outside.isValid(), "Stronghold generated on an orbital planet");
            }
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            var json = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow();
            var restored = (SolarChunkGenerator) ChunkGenerator.CODEC.parse(ops, json).getOrThrow();
            restored.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
            helper.assertTrue(restored.solarSettings().equals(options), "Solar configuration was not persisted");
            helper.assertTrue(restored.layout().systemAtCell(-2, 3).equals(generator.layout().systemAtCell(-2, 3)), "Reload changed the layout");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void naturalPortalWritesAreScoped(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var geometry = new SolarLayout(4, SolarLayout.Params.defaults());
        var system = geometry.systemAtCell(0, 0);
        var locations = new java.util.ArrayList<BlockPos>();
        locations.add(BlockPos.containing(system.central().x(), 32, system.central().z()));
        var orbital = system.bodies().get(1);
        locations.add(BlockPos.containing(orbital.x(), orbital.y(), orbital.z()));
        locations.add(new BlockPos(0, 64, 0));
        for (var moon : system.moons()) {
            var s = moon.sphere();
            locations.add(BlockPos.containing(s.centerX(), s.centerY(), s.centerZ()));
        }
        var center = system.central();
        locations.add(BlockPos.containing(center.x() + center.radius() - 2, center.y(), center.z()));
        locations.add(BlockPos.containing(center.x() + center.radius() - 3, center.y(), center.z()));
        var roomField = SolarPortalGuard.class.getDeclaredField("PORTAL_ROOM");
        roomField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var roomScope = (ThreadLocal<net.minecraft.world.level.levelgen.structure.BoundingBox>) roomField.get(null);
        var previousScope = roomScope.get();
        var centralRoom = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                (int) center.x() - 4, 28, (int) center.z() - 4, (int) center.x() + 4, 36, (int) center.z() + 4);
        var orbitalRoom = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                (int) orbital.x() - 4, (int) orbital.y() - 4, (int) orbital.z() - 4,
                (int) orbital.x() + 4, (int) orbital.y() + 4, (int) orbital.z() + 4);
        var boundaryRoom = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                (int) center.x() - center.radius(), SolarLayout.MIN_Y, (int) center.z() - center.radius(),
                (int) center.x() + center.radius(), SolarLayout.MAX_Y, (int) center.z() + center.radius());
        try {
            for (var room : new net.minecraft.world.level.levelgen.structure.BoundingBox[]{null, centralRoom, orbitalRoom, boundaryRoom}) {
                if (room == null) roomScope.remove();
                else roomScope.set(room);
                for (var pos : locations) {
                    // Literal policy previously duplicated by both mixins.
                    var body = geometry.bodyAt(pos.getX(), pos.getY(), pos.getZ(), 3);
                    boolean expected = room != null && room.isInside(pos) && body != null && body.central();
                    helper.assertTrue(SolarPortalGuard.allowsNaturalPortal(geometry, pos) == expected,
                            "Shared portal authorization changed the previous policy");
                    var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level,
                            level.registryAccess().registryOrThrow(Registries.BIOME), null);
                    ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(geometry);
                    for (var block : java.util.List.of(Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL)) {
                        chunk.setBlockState(pos, block.defaultBlockState(), false);
                        helper.assertTrue(chunk.getBlockState(pos).is(expected ? block : Blocks.AIR),
                                "Only the central portal-room scope may write natural portal blocks");
                    }
                }
            }
        } finally {
            if (previousScope == null) roomScope.remove();
            else roomScope.set(previousScope);
        }
        var vanilla = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        var pos = new BlockPos(0, 64, 0);
        vanilla.setBlockState(pos, Blocks.END_PORTAL_FRAME.defaultBlockState(), false);
        helper.assertTrue(vanilla.getBlockState(pos).is(Blocks.END_PORTAL_FRAME), "Non-Solar portal writes changed");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void solarHeightmapsIgnoreShellDuringUpdates(GameTestHelper helper) {
        var level = helper.getLevel();
        var geometry = new SolarLayout(4, SolarLayout.Params.defaults());
        var body = geometry.systemAtCell(0, 0).central();
        var base = BlockPos.containing(body.x(), 64, body.z());
        var chunk = new ProtoChunk(new ChunkPos(base), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(geometry);
        chunk.setBlockState(base, Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(BlockPos.containing(body.x(), body.y() + body.radius(), body.z()), Blocks.GLASS.defaultBlockState(), false);
        chunk.setBlockState(BlockPos.containing(body.x(), body.y() - body.radius(), body.z()), Blocks.GLASS.defaultBlockState(), false);
        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
        var heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        helper.assertTrue(heightmap.getFirstAvailable(base.getX() & 15, base.getZ() & 15) == 65, "Shell contaminated the primed heightmap");
        chunk.setBlockState(base, Blocks.AIR.defaultBlockState(), false);
        heightmap.update(base.getX() & 15, base.getY(), base.getZ() & 15, Blocks.AIR.defaultBlockState());
        helper.assertTrue(heightmap.getFirstAvailable(base.getX() & 15, base.getZ() & 15) == level.getMinBuildHeight(),
                "Downward heightmap scan treated the bottom shell as terrain");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void centralSurfaceIsExtremeAndPreservesPortalDepth(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var defaults = SolarSettings.defaults();
        var p = defaults.geometry();
        var finish = SolarChunkGenerator.class.getDeclaredMethod("paintVolume", net.minecraft.world.level.chunk.ChunkAccess.class,
                mx.issacinzunza.biosphereworlds.worldgen.solar.SolarVolumePainter.Pass.class);
        finish.setAccessible(true);
        for (boolean cold : new boolean[]{false, true}) {
            var geometry = new SolarLayout.Params(p.systemRadius(), p.systemGap(), p.planetCount(), p.minRadius(), p.maxRadius(),
                    p.centralRadius(), p.minGap(), p.maxGap(), cold, p.innerPercent(), p.middlePercent(), p.outerPercent(),
                    p.satelliteCount(), p.satelliteMinElevation(), p.satelliteMaxElevation(), p.satelliteGap());
            var settings = new SolarSettings(geometry, true, cold ? SolarLayout.Climate.COLD : SolarLayout.Climate.HOT,
                    defaults.guardians(), defaults.environment(), defaults.general(), 2);
            var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, settings);
            var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), 4);
            generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, 4);
            var center = generator.layout().systemAtCell(0, 0).central();
            int x = (int) center.x();
            int z = (int) center.z();
            var biome = generator.getBiomeSource().getNoiseBiome(Math.floorDiv(x, 4), 16, Math.floorDiv(z, 4), random.sampler());
            helper.assertTrue(biome.is(cold ? Biomes.ICE_SPIKES : Biomes.BADLANDS), "Central biome does not match its extreme profile");
            var chunk = new ProtoChunk(new ChunkPos(new BlockPos(x, 64, z)), UpgradeData.EMPTY, level,
                    registries.registryOrThrow(Registries.BIOME), null);
            ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(generator.layout());
            for (int y = 30; y <= 75; y++) chunk.setBlockState(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), false);
            chunk.setBlockState(new BlockPos(x + 1, 63, z), Blocks.WATER.defaultBlockState(), false);
            // Fixture only: emulate an existing room block below the surface transformation.
            chunk.getSection(chunk.getSectionIndex(32)).setBlockState(x & 15, 32 & 15, z & 15, Blocks.END_PORTAL_FRAME.defaultBlockState());
            finish.invoke(generator, chunk, SolarVolumePainter.Pass.SURFACE);
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, 75, z)).is(cold ? Blocks.PACKED_ICE : Blocks.MAGMA_BLOCK),
                    "Central terrain still has an ordinary surface");
            helper.assertTrue(chunk.getBlockState(new BlockPos(x + 1, 63, z)).is(cold ? Blocks.PACKED_ICE : Blocks.LAVA),
                    "Central water was not converted to its dangerous profile");
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, 32, z)).is(Blocks.END_PORTAL_FRAME), "Central surface overwrote the portal depth");
            var stronghold = registries.registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(
                    Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("stronghold")));
            var start = stronghold.value().generate(registries, generator, generator.getBiomeSource(), random,
                    level.getStructureManager(), 4, chunk.getPos(), 0, level, stronghold.value().biomes()::contains);
            helper.assertTrue(start.isValid() && start.getPieces().stream().filter(piece -> piece instanceof StrongholdPieces.PortalRoom).count() == 1,
                    "Extreme central profile rejected the complete stronghold");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void oldSnapshotsKeepTheirOriginalLayout(GameTestHelper helper) {
        var p = new SolarLayout.Params(2000, 1500, 8, 64, 112, 180, 100, 400, false, 30, 40, 30, 3, 30, 55, 16);
        var saved = new SolarSettings(p, true, SolarLayout.Climate.TEMPERATE, SolarSettings.defaults().guardians(),
                new SolarSettings.Environment(false, 20, 20, true), SolarSettings.General.legacy(), 1);
        var json = SolarSettings.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow().getAsJsonObject();
        json.remove("general");
        json.remove("layout_version");
        var restored = SolarSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertTrue(restored.equals(saved), "An old Solar snapshot silently adopted new settings");
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, restored);
        var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), 4);
        generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, 4);
        var expected = new SolarLayout(4, p, 1, SolarLayout.PlacementOptions.defaults());
        for (int cell = -3; cell <= 3; cell++) {
            helper.assertTrue(generator.layout().systemAtCell(cell, -cell).equals(expected.systemAtCell(cell, -cell)),
                    "Old planet or satellite positions changed");
        }
        helper.succeed();
    }
}
