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
    @GameTest(template = "solar_empty")
    public static void orbitalCagesAreGeneratedOnlyWhenEnabled(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var defaults = SolarSettings.defaults();
        for (boolean enabled : new boolean[]{false, true}) {
            var guardians = new SolarSettings.Guardians(enabled, true, true, 48, 2, 3, 600);
            var settings = new SolarSettings(defaults.geometry(), true, SolarLayout.Climate.HOT, guardians, defaults.environment(), defaults.general(), 4);
            var generator = new SolarChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), empty, noise, settings);
            var random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), 19);
            generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), random, 19);
            var posts = generator.layout().guardianPosts(generator.layout().systemAtCell(0, 0));
            helper.assertTrue(posts.size() == 3, "The default sun should have one cage site for each guardian type");
            for (var post : posts) {
                var pos = new BlockPos(post.x(), post.y(), post.z());
                var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level, registries.registryOrThrow(Registries.BIOME), null);
                ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(generator.layout());
                helper.assertTrue(generator.layout().bodyAt(post.x(), post.y(), post.z(), 0) == null, "Cages must be outside the sun");
                mx.issacinzunza.biosphereworlds.event.SolarEvents.placeGuardianSpawners(chunk, generator);
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
                var block = mx.issacinzunza.biosphereworlds.event.SolarEvents.guardianSpawner(pos, index);
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
        var finish = SolarChunkGenerator.class.getDeclaredMethod("finishVolume", net.minecraft.world.level.chunk.ChunkAccess.class, boolean.class);
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
            finish.invoke(generator, chunk, true);
            helper.assertTrue(chunk.getBlockState(pos).is(cap ? Blocks.GLASS : Blocks.STONE), "Top closure must follow close_open_top exactly");
            helper.assertTrue(SolarChunkGenerator.heightmapState(chunk, pos, chunk.getBlockState(pos)).is(cap ? Blocks.AIR : Blocks.STONE), "Heightmaps must ignore an enabled cap and preserve terrain when disabled");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_empty")
    public static void solarUsesIndependentConfiguration(GameTestHelper helper) {
        var settings = SolarConfig.snapshot();
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
        var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var empty = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        var parameters = registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var stronghold = registries.registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(
                Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("stronghold")));
        var options = SolarSettings.defaults();
        for (long seed : new long[]{0, -1, Long.MIN_VALUE, 81723}) {
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
    public static void naturalPortalWritesAreScoped(GameTestHelper helper) {
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
        for (var pos : locations) {
            var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level,
                    level.registryAccess().registryOrThrow(Registries.BIOME), null);
            ((SolarChunkAccess) chunk).biosphereworlds$solarLayout(geometry);
            chunk.setBlockState(pos, Blocks.END_PORTAL_FRAME.defaultBlockState(), false);
            helper.assertTrue(chunk.getBlockState(pos).isAir(), "Natural portal frame accepted outside a stronghold portal-room placement");
            chunk.setBlockState(pos, Blocks.END_PORTAL.defaultBlockState(), false);
            helper.assertTrue(chunk.getBlockState(pos).isAir(), "Natural End portal accepted outside a stronghold portal-room placement");
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
        var finish = SolarChunkGenerator.class.getDeclaredMethod("finishVolume", net.minecraft.world.level.chunk.ChunkAccess.class,
                boolean.class, boolean.class);
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
            finish.invoke(generator, chunk, false, true);
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
