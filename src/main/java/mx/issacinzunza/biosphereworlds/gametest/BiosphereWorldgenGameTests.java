package mx.issacinzunza.biosphereworlds.gametest;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereNoiseChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Contratos clasicos que necesitan los mixins y el motor real de Minecraft. */
@GameTestHolder(BiosphereWorlds.MODID)
@PrefixGameTestTemplate(false)
public final class BiosphereWorldgenGameTests {
    private BiosphereWorldgenGameTests() { }

    @GameTest(template = "solar_empty")
    public static void aquiferUsesItsOwnerAfterConstruction(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var random = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), 81723);
        var proto = chunk(helper, new BlockPos(250, 62, 250));
        var beardifier = new DensityFunctions.BeardifierOrMarker() {
            @Override public double compute(DensityFunction.FunctionContext context) { return 0; }
            @Override public double minValue() { return 0; }
            @Override public double maxValue() { return 0; }
        };
        var noise = NoiseChunk.forChunk(proto, random, beardifier, settings,
                (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
        var aquifer = noise.aquifer();
        helper.assertTrue(aquifer instanceof Aquifer.NoiseBasedAquifer, "Fixture needs an actual aquifer");
        var outside = new DensityFunction.SinglePointContext(250, 62, 250);
        helper.assertTrue(aquifer.computeSubstance(outside, 1) == null, "Unmarked vanilla substance changed");
        BiosphereWorldgen.markNoiseChunk(noise);
        helper.assertTrue(aquifer.computeSubstance(outside, 1).isAir(), "Late owner marking must affect its existing aquifer");
        helper.assertTrue(aquifer.computeSubstance(new DensityFunction.SinglePointContext(0, 62, 0), 1) == null,
                "The sphere interior must keep the vanilla aquifer path");
        ((BiosphereNoiseChunkAccess) noise).biosphereworlds$setBiosphere(false);
        helper.assertTrue(aquifer.computeSubstance(outside, 1) == null, "The aquifer must read its owner, not a stale global set");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void classicSatelliteHeightmapsUseWorldCoordinates(GameTestHelper helper) {
        var layout = SphereLayout.current();
        for (int sign : new int[]{-1, 1}) {
            SphereLayout.Satellite moon = null;
            for (int cell = 2; cell < 30 && moon == null; cell++) {
                moon = layout.satellitesAtCell(sign * cell, sign * cell).stream()
                        .filter(s -> s.centerY() + s.radius() < 315).findFirst().orElse(null);
            }
            helper.assertTrue(moon != null, "Default classic fixture must have a satellite away from the origin");
            var pos = BlockPos.containing(moon.centerX(), moon.centerY(), moon.centerZ());
            var ground = new BlockPos(pos.getX(), 0, pos.getZ());
            var top = new BlockPos(pos.getX(), (int) Math.ceil(moon.centerY() + moon.radius()) + 1, pos.getZ());
            helper.assertTrue(layout.isInsideSatellite(pos.getX(), pos.getY(), pos.getZ()), "Fixture must be inside the moon");
            var classic = chunk(helper, pos);
            ((BiosphereChunkAccess) classic).biosphereworlds$setBiosphere(true);
            var vanilla = chunk(helper, pos);
            classic.setBlockState(ground, Blocks.STONE.defaultBlockState(), false);
            vanilla.setBlockState(ground, Blocks.STONE.defaultBlockState(), false);
            for (var material : new net.minecraft.world.level.block.Block[]{Blocks.STONE, Blocks.MAGMA_BLOCK, Blocks.DIAMOND_ORE, Blocks.GLASS}) {
                classic.setBlockState(pos, material.defaultBlockState(), false);
                vanilla.setBlockState(pos, material.defaultBlockState(), false);
                var types = java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);
                Heightmap.primeHeightmaps(classic, types);
                Heightmap.primeHeightmaps(vanilla, types);
                for (var type : types) {
                    var heightmap = classic.getOrCreateHeightmapUnprimed(type);
                    int x = pos.getX() & 15;
                    int z = pos.getZ() & 15;
                    helper.assertTrue(heightmap.getFirstAvailable(x, z) == 1, "Satellite contaminated classic priming: " + material);
                    helper.assertTrue(vanilla.getOrCreateHeightmapUnprimed(type).getFirstAvailable(x, z) == pos.getY() + 1,
                            "Unmarked vanilla priming changed");
                    heightmap.update(x, pos.getY(), z, material.defaultBlockState());
                    helper.assertTrue(heightmap.getFirstAvailable(x, z) == 1, "Satellite contaminated classic update");
                    classic.setBlockState(top, Blocks.STONE.defaultBlockState(), false);
                    heightmap.update(x, top.getY(), z, Blocks.STONE.defaultBlockState());
                    helper.assertTrue(heightmap.getFirstAvailable(x, z) == top.getY() + 1, "Real terrain above the moon must count");
                    classic.setBlockState(top, Blocks.AIR.defaultBlockState(), false);
                    heightmap.update(x, top.getY(), z, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(heightmap.getFirstAvailable(x, z) == 1, "Downward scan counted satellite material");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void classicDisabledSatellitesSkipInvalidCatalogBeforeBiomes(GameTestHelper helper) throws ReflectiveOperationException {
        var registry = helper.getLevel().registryAccess();
        var noise = registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                Registries.NOISE_SETTINGS, BiosphereWorlds.prefix("biospheres")));
        var parameters = registry.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(parameters), noise);
        var random = RandomState.create(noise.value(), registry.lookupOrThrow(Registries.NOISE), 81723);
        var enabled = mx.issacinzunza.biosphereworlds.config.SphereConfig.SATELLITES_ENABLED;
        var count = mx.issacinzunza.biosphereworlds.config.SphereConfig.SATELLITES_MAX_COUNT;
        var includes = mx.issacinzunza.biosphereworlds.config.SphereConfig.SATELLITES_INCLUDED_ORES;
        var excludes = mx.issacinzunza.biosphereworlds.config.SphereConfig.SATELLITES_EXCLUDED_ORES;
        var weight = mx.issacinzunza.biosphereworlds.config.SphereConfig.SATELLITES_MOD_ORE_WEIGHT;
        boolean oldEnabled = enabled.get();
        int oldCount = count.get(), oldWeight = weight.get();
        var oldIncludes = includes.get();
        var oldExcludes = excludes.get();
        var cache = mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog.class.getDeclaredField("candidates");
        cache.setAccessible(true);
        var oldCache = cache.get(null);
        try {
            includes.set(java.util.List.of("minecraft:stone"));
            excludes.set(mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.VANILLA_MINERALS.stream()
                    .map(mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.Mineral::blockId).toList());
            weight.set(0);
            cache.set(null, null);
            for (boolean byCount : new boolean[]{false, true}) {
                enabled.set(byCount);
                count.set(byCount ? 0 : 3);
                var proto = chunk(helper, helper.absolutePos(BlockPos.ZERO));
                var pending = generator.createBiomes(random, Blender.empty(), helper.getLevel().structureManager(), proto);
                // El worker consulta estructuras mediante tareas del servidor: atenderlas antes de join.
                pending.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
                helper.getLevel().getServer().managedBlock(() -> {
                    helper.getLevel().getChunkSource().pollTask();
                    return pending.isDone();
                });
                pending.join();
                helper.assertTrue(cache.get(null) == null, "Disabled classic satellites must not resolve an invalid catalog");
            }
            enabled.set(true);
            count.set(3);
            boolean rejected = false;
            try {
                generator.createBiomes(random, Blender.empty(), helper.getLevel().structureManager(),
                        chunk(helper, helper.absolutePos(BlockPos.ZERO))).join();
            } catch (IllegalArgumentException expected) {
                rejected = expected.getMessage().contains("positive-weight");
            }
            helper.assertTrue(rejected && cache.get(null) == null, "Enabled classic generation must reject invalid minerals before publishing or writing blocks");
        } finally {
            enabled.set(oldEnabled);
            count.set(oldCount);
            includes.set(oldIncludes);
            excludes.set(oldExcludes);
            weight.set(oldWeight);
            cache.set(null, oldCache);
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void classicSatelliteWriterMatchesLiteralMaterials(GameTestHelper helper) throws ReflectiveOperationException {
        var method = mx.issacinzunza.biosphereworlds.carver.SphereCarver.class.getDeclaredMethod("carveSatellite",
                net.minecraft.world.level.chunk.ChunkAccess.class, SphereLayout.Satellite.class,
                net.minecraft.world.level.block.state.BlockState.class, int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        var carver = new mx.issacinzunza.biosphereworlds.carver.SphereCarver(
                mx.issacinzunza.biosphereworlds.carver.SphereCarver.SphereCarverConfig.CODEC);
        var candidates = mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog.candidates();
        for (int radius : new int[]{2, 8, 16}) for (int sign : new int[]{-1, 1}) {
            var moon = new SphereLayout.Satellite(sign * 63.75, 180.25, sign * 63.5, radius, 81723);
            var shell = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
            var layers = mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.layers(radius);
            var mineral = layers.hasCore() ? mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.selectMineral(moon.seed(), candidates) : null;
            var ore = mineral == null ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(mineral.blockId())).defaultBlockState();
            int centerX = Math.floorDiv((int) Math.floor(moon.centerX()), 16);
            int centerZ = Math.floorDiv((int) Math.floor(moon.centerZ()), 16);
            for (int cx = centerX - 1; cx <= centerX + 1; cx++) for (int cz = centerZ - 1; cz <= centerZ + 1; cz++) {
                var chunk = chunk(helper, new BlockPos(cx * 16, 0, cz * 16));
                method.invoke(carver, chunk, moon, shell, cx * 16, cx * 16 + 15, cz * 16, cz * 16 + 15);
                for (int x = cx * 16; x <= cx * 16 + 15; x++) for (int z = cz * 16; z <= cz * 16 + 15; z++) {
                    for (int y = (int) Math.floor(moon.centerY() - radius) - 1; y <= Math.ceil(moon.centerY() + radius) + 1; y++) {
                        // Copia literal del orden de operaciones y switch anterior a la extraccion.
                        double dx = x - moon.centerX();
                        double dz = z - moon.centerZ();
                        double dy = y - moon.centerY();
                        double distance = (dx * dx + dz * dz) + dy * dy;
                        var expected = distance > (double) radius * radius ? Blocks.AIR.defaultBlockState()
                                : switch (mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.regionAt(layers, distance)) {
                                    case SHELL -> shell;
                                    case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
                                    case STONE -> Blocks.STONE.defaultBlockState();
                                    case CORE -> mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.isOre(
                                            moon.seed(), x, y, z, mineral.densityPermille()) ? ore : Blocks.STONE.defaultBlockState();
                                };
                        helper.assertTrue(chunk.getBlockState(new BlockPos(x, y, z)).equals(expected), "Classic satellite writer changed a block");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void legacyBiomeSearchContinuesPastOutsideCandidate(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var biomes = registries.registryOrThrow(Registries.BIOME);
        var plains = biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var empty = biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.THE_VOID);
        var wrapper = new mx.issacinzunza.biosphereworlds.worldgen.BiosphereBiomeSource(
                new net.minecraft.world.level.biome.FixedBiomeSource(plains), empty);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        var saved = net.minecraft.world.level.biome.BiomeSource.CODEC.encodeStart(ops, wrapper).getOrThrow();
        var restored = net.minecraft.world.level.biome.BiomeSource.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(restored.possibleBiomes().contains(empty), "The historical codec must advertise the void it returns");
        var found = restored.findBiomeHorizontal(250, 62, 0, 300, 1, plains::equals,
                net.minecraft.util.RandomSource.create(0), true, null);
        helper.assertTrue(found != null, "Search must continue after the closest underlying candidate lies outside a sphere");
        var pos = found.getFirst();
        helper.assertTrue(SphereLayout.current().isInside(pos.getX(), pos.getY(), pos.getZ(), -4),
                "Returned biome must belong to an actual sphere");
        var voidResult = restored.findBiomeHorizontal(250, 62, 0, 300, 1, empty::equals,
                net.minecraft.util.RandomSource.create(0), true, null);
        helper.assertTrue(voidResult != null, "Searching the void must also work through the historical codec");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void modSnowSkipsVanillaBeforeReadingWorldOrRandom(GameTestHelper helper) {
        var vanilla = helper.getLevel().getChunkSource().getGenerator();
        helper.assertTrue(!BiosphereWorldgen.isBiosphereGenerator(vanilla), "Fixture must use a vanilla generator");
        var forbiddenLevel = (net.minecraft.world.level.WorldGenLevel) java.lang.reflect.Proxy.newProxyInstance(
                BiosphereWorldgenGameTests.class.getClassLoader(), new Class<?>[]{net.minecraft.world.level.WorldGenLevel.class},
                (proxy, method, args) -> { throw new AssertionError("Vanilla snow gate touched the world: " + method.getName()); });
        var config = net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE;
        // Null random vuelve visible cualquier acceso indebido antes del gate.
        var context = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(java.util.Optional.empty(),
                forbiddenLevel, vanilla, null, BlockPos.ZERO, config);
        helper.assertTrue(!mx.issacinzunza.biosphereworlds.registry.ModFeature.SPHERE_SNOW.get().place(context),
                "The mod snow feature must be an exact no-op in vanilla");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void configMaterialsAndMineralsValidateTheirRealConsumers(GameTestHelper helper) throws ReflectiveOperationException {
        var validate = mx.issacinzunza.biosphereworlds.config.SphereConfig.class
                .getDeclaredMethod("validateBlockName", Object.class);
        validate.setAccessible(true);
        helper.assertTrue((boolean) validate.invoke(null, "minecraft:glass"), "A real block must be accepted");
        for (String invalid : new String[]{"minecraft:stick", "bad id", "missing:unknown_block"}) {
            helper.assertTrue(!(boolean) validate.invoke(null, invalid), "Invalid block accepted: " + invalid);
        }
        var excluded = mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.VANILLA_MINERALS.stream()
                .map(mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers.Mineral::blockId).toList();
        var result = mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog.resolve(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, java.util.List.of("minecraft:stone"), excluded, 0, 20);
        helper.assertTrue(result.candidates().stream().allMatch(m -> m.weight() == 0), "Fixture must contain only zero weights");
        for (String preset : new String[]{"Classic Biospheres", "Solar"}) {
            boolean rejected = false;
            try { result.requireSelectable(preset); }
            catch (IllegalArgumentException expected) {
                rejected = expected.getMessage().contains("positive-weight");
            }
            helper.assertTrue(rejected, preset + " must reject zero weights before selection");
        }
        var valid = mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog.resolve(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK, java.util.List.of("minecraft:stone"), excluded, 1, 20);
        helper.assertTrue(valid.requireSelectable("Classic Biospheres") == valid.candidates(), "Validation must preserve the exact snapshot");
        helper.assertTrue(valid.candidates().stream().noneMatch(m -> excluded.contains(m.blockId())), "Excluded vanilla ores must not return via fallback");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void densityBoundsRemainValidWhenComposed(GameTestHelper helper) {
        var density = new mx.issacinzunza.biosphereworlds.density.MultipleSpheresDistanceFunction(500, 128, 62);
        var context = new DensityFunction.SinglePointContext(250, 62, 250);
        double value = density.compute(context);
        helper.assertTrue(value == 7.62939453125 && value <= density.maxValue(), "Declared range must contain the actual density");
        helper.assertTrue(DensityFunctions.max(density, DensityFunctions.constant(4)).compute(context) == value,
                "MAX must not discard density through a false upper bound");
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void loadedChunkUsesItsActualWorldInsteadOfStaleMarker(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(!BiosphereWorldgen.isBiosphereGenerator(level.getChunkSource().getGenerator()),
                "Fixture must run in the ordinary GameTest world");
        var proto = chunk(helper, helper.absolutePos(BlockPos.ZERO));
        ((BiosphereChunkAccess) proto).biosphereworlds$setBiosphere(true);
        helper.assertTrue(BiosphereWorldgen.isBiosphereChunk(proto), "ProtoChunk must honor its generation marker");
        var promoted = new LevelChunk(level, proto, null);
        ((BiosphereChunkAccess) promoted).biosphereworlds$setBiosphere(true);
        helper.assertTrue(!BiosphereWorldgen.isBiosphereChunk(promoted), "LevelChunk must use its real generator, never a stale marker");
        helper.succeed();
    }

    private static ProtoChunk chunk(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        return new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    @GameTest(template = "solar_empty", timeoutTicks = 1200)
    public static void classicShellSwitchesPreserveLiteralDefaultsAndIndependentBedrock(GameTestHelper helper) {
        var configured = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_CARVER)
                .getHolderOrThrow(mx.issacinzunza.biosphereworlds.registry.ModCarvers.BIOSPHERE_CARVER_KEY).value();
        for (boolean upper : new boolean[]{false, true}) for (boolean lower : new boolean[]{false, true}) {
            for (boolean legacy : new boolean[]{false, true}) {
                var recipe = new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings(
                        new SphereLayout.Params(500, 62, 8, 8), java.util.List.of("minecraft:blue_stained_glass"),
                        mx.issacinzunza.biosphereworlds.config.SphereConfig.BedrockMode.SHELL, legacy, false,
                        new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bridges(false, 1, "minecraft:oak_planks"),
                        new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Ocean(false, 20, 20),
                        new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Structures(true, 16), java.util.List.of(), upper, lower);
                for (int cx : new int[]{-1, 0}) for (int cz : new int[]{-1, 0}) {
                    var proto = chunk(helper, new BlockPos(cx * 16, 0, cz * 16));
                    ((BiosphereChunkAccess) proto).biosphereworlds$setBiosphere(true);
                    ((BiosphereChunkAccess) proto).biosphereworlds$setClassicWorld(new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound(recipe));
                    for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        for (int y = 52; y <= 72; y++) proto.setBlockState(new BlockPos(x, y, z), shellFixtureState(x, y, z), false);
                    }
                    // Este carver no consulta contexto, acuifero ni biomas; la llamada recorre su escritor real.
                    configured.carve(null, proto, pos -> null, net.minecraft.util.RandomSource.create(1), null,
                            proto.getPos(), proto.getOrCreateCarvingMask(net.minecraft.world.level.levelgen.GenerationStep.Carving.AIR));
                    for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        for (int y = 52; y <= 72; y++) {
                            var original = shellFixtureState(x, y, z);
                            var expected = original;
                            boolean oldMode = upper && lower && legacy;
                            int radius = oldMode ? 7 : 8;
                            double dy = y - 62;
                            double distanceSq = (double) x * x + (double) z * z + dy * dy;
                            double innerRadiusSq = (radius - 2.0) * (radius - 2.0);
                            double linerInnerRadius = radius - 2.0 - 3;
                            if (linerInnerRadius < 0.0) linerInnerRadius = 0.0;
                            // Copia literal de las condiciones y reemplazos anteriores para ambas mitades activas.
                            if (distanceSq <= radius * radius && distanceSq >= innerRadiusSq) {
                                if (upper && lower) {
                                    if (!oldMode || original.is(net.minecraft.tags.BlockTags.AIR) || original.is(Blocks.WATER)) {
                                        if (!(oldMode && y < 55 && !original.is(Blocks.WATER))) expected = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                                    }
                                } else if ((y >= 62 ? upper : lower)) expected = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                                else if (original.is(Blocks.WATER)) expected = Blocks.GLASS.defaultBlockState();
                            } else if (!oldMode && dy <= 0.0 && distanceSq >= linerInnerRadius * linerInnerRadius && distanceSq < innerRadiusSq) {
                                expected = Blocks.BEDROCK.defaultBlockState();
                            }
                            helper.assertTrue(proto.getBlockState(new BlockPos(x, y, z)).equals(expected),
                                    "Classic shell mismatch at " + x + "," + y + "," + z + " flags=" + upper + "," + lower + " legacy=" + legacy);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    private static net.minecraft.world.level.block.state.BlockState shellFixtureState(int x, int y, int z) {
        return switch (Math.floorMod(x + y + z, 3)) {
            case 0 -> Blocks.STONE.defaultBlockState();
            case 1 -> Blocks.WATER.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    @GameTest(template = "solar_empty")
    public static void openClassicHeightmapsIgnoreBarriersAndLinerButKeepDryTerrain(GameTestHelper helper) {
        var recipe = new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings(
                new SphereLayout.Params(500, 62, 8, 8), java.util.List.of("minecraft:blue_stained_glass"),
                mx.issacinzunza.biosphereworlds.config.SphereConfig.BedrockMode.SHELL, false, false,
                new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bridges(false, 1, "minecraft:oak_planks"),
                new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Ocean(false, 20, 20),
                new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Structures(true, 16), java.util.List.of(), false, false);
        for (var material : new net.minecraft.world.level.block.Block[]{Blocks.GLASS, Blocks.BEDROCK, Blocks.STONE}) {
            var pos = material == Blocks.BEDROCK ? new BlockPos(4, 61, 0) : new BlockPos(-6, 62, 0);
            var proto = chunk(helper, pos);
            ((BiosphereChunkAccess) proto).biosphereworlds$setBiosphere(true);
            ((BiosphereChunkAccess) proto).biosphereworlds$setClassicWorld(new mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound(recipe));
            proto.setBlockState(new BlockPos(pos.getX(), 0, pos.getZ()), Blocks.STONE.defaultBlockState(), false);
            proto.setBlockState(pos, material.defaultBlockState(), false);
            var types = java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);
            Heightmap.primeHeightmaps(proto, types);
            for (var type : types) {
                var map = proto.getOrCreateHeightmapUnprimed(type);
                int x = pos.getX() & 15, z = pos.getZ() & 15;
                int expected = material == Blocks.STONE ? pos.getY() + 1 : 1;
                helper.assertTrue(map.getFirstAvailable(x, z) == expected, "Open Classic heightmap counted containment or lost dry terrain");
                var top = pos.above(30);
                proto.setBlockState(top, Blocks.STONE.defaultBlockState(), false);
                map.update(x, top.getY(), z, Blocks.STONE.defaultBlockState());
                proto.setBlockState(top, Blocks.AIR.defaultBlockState(), false);
                map.update(x, top.getY(), z, Blocks.AIR.defaultBlockState());
                helper.assertTrue(map.getFirstAvailable(x, z) == expected, "Downward heightmap scan counted containment or lost dry terrain");
            }
        }
        helper.succeed();
    }
}
