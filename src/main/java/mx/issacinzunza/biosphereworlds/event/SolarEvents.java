package mx.issacinzunza.biosphereworlds.event;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.LinkedHashMap;


@EventBusSubscriber
public final class SolarEvents {
    private static final String GUARDIAN = "biosphereworlds_solar_guardian";
    private SolarEvents() { }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        // No chunk access/loading here: restore only the per-chunk geometry reference.
        ((mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkAccess) event.getChunk()).biosphereworlds$solarLayout(generator.layout());
    }

    @SubscribeEvent public static void spawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            throw new IllegalStateException("Solar Systems requires Generate Structures: each system must contain a stronghold");
        }
        var planet = generator.layout().systemAtCell(0, 0).bodies().stream()
                .filter(b -> !b.central() && b.climate() == SolarLayout.Climate.TEMPERATE).findFirst().orElseThrow();
        int x = (int) Math.floor(planet.x());
        int z = (int) Math.floor(planet.z());
        int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
        event.getSettings().setSpawn(new BlockPos(x, y, z), 0);
        event.setCanceled(true);
    }

    /** Old snapshots retain their previous spawning behavior; new worlds use cages. */
    @SubscribeEvent public static void legacyGuardians(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)
                || generator.solarSettings().layoutVersion() >= 4) return;
        var config = generator.solarSettings().guardians();
        if (!config.enabled() || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || level.getGameTime() % config.interval() != 0) return;
        var state = level.getChunkSource().getLastSpawnState();
        if (state == null) return;
        int budget = MobCategory.MONSTER.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / (17 * 17)
                - state.getMobCategoryCounts().getInt(MobCategory.MONSTER);
        if (budget <= 0) return;
        var active = new LinkedHashMap<Long, SolarLayout.Body>();
        for (var player : level.players()) {
            if (player.isSpectator()) continue;
            var system = generator.layout().systemAtBlock(player.getX(), player.getZ());
            var body = system.central();
            double range = body.radius() + config.radius() + 128;
            if (player.distanceToSqr(body.x(), body.y(), body.z()) <= range * range) active.put(system.id(), body);
        }
        for (var body : active.values()) {
            if (budget <= 0) break;
            if (config.ghasts() && legacyTrySpawn(level, body, EntityType.GHAST, config.ghastMax(), config.radius())) budget--;
            if (budget > 0 && config.phantoms() && level.isNight()
                    && legacyTrySpawn(level, body, EntityType.PHANTOM, config.phantomMax(), config.radius())) budget--;
        }
    }

    private static boolean legacyTrySpawn(ServerLevel level, SolarLayout.Body body, EntityType<? extends Mob> type, int max, int band) {
        if (max == 0) return false;
        double extent = body.radius() + band + 128;
        var area = new AABB(body.x() - extent, level.getMinBuildHeight(), body.z() - extent,
                body.x() + extent, level.getMaxBuildHeight(), body.z() + extent);
        if (level.getEntitiesOfClass(Mob.class, area, e -> e.getType() == type).size() >= max) return false;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * 2 * Math.PI;
            double elevation = level.random.nextDouble() * 0.6;
            double distance = body.radius() + 8 + level.random.nextDouble() * Math.max(1, band - 8);
            double horizontal = Math.cos(elevation) * distance;
            double x = body.x() + Math.cos(angle) * horizontal;
            double y = body.y() + Math.sin(elevation) * distance;
            double z = body.z() + Math.sin(angle) * horizontal;
            Mob mob = type.create(level);
            if (mob == null) return false;
            mob.moveTo(x, y, z, level.random.nextFloat() * 360, 0);
            AABB box = mob.getBoundingBox();
            if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()) continue;
            boolean loaded = true;
            for (int cx = Math.floorDiv((int) Math.floor(box.minX), 16); cx <= Math.floorDiv((int) Math.floor(box.maxX), 16); cx++) {
                for (int cz = Math.floorDiv((int) Math.floor(box.minZ), 16); cz <= Math.floorDiv((int) Math.floor(box.maxZ), 16); cz++) {
                    if (level.getChunkSource().getChunkNow(cx, cz) == null) loaded = false;
                }
            }
            if (!loaded || level.hasNearbyAlivePlayer(x, y, z, 24) || !level.hasNearbyAlivePlayer(x, y, z, 128)
                    || !level.noCollision(mob) || !level.getEntities(mob, box).isEmpty()) continue;
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.EVENT, null);
            mob.getPersistentData().putBoolean(GUARDIAN, true);
            return level.addFreshEntity(mob);
        }
        return false;
    }

    /** Called once during decoration, after Solar's volume trim. Never repairs player-broken cages. */
    public static void placeGuardianSpawners(net.minecraft.world.level.chunk.ChunkAccess chunk, SolarChunkGenerator generator) {
        if (!generator.solarSettings().guardians().enabled()) return;
        var layout = generator.layout();
        var system = layout.systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
        for (var post : layout.guardianPosts(system)) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                var floor = new BlockPos(post.x() + dx, post.y() - 1, post.z() + dz);
                if (new net.minecraft.world.level.ChunkPos(floor).equals(chunk.getPos())) chunk.setBlockState(floor, Blocks.OBSIDIAN.defaultBlockState(), false);
            }
            var pos = new BlockPos(post.x(), post.y(), post.z());
            if (!new net.minecraft.world.level.ChunkPos(pos).equals(chunk.getPos())) continue;
            chunk.setBlockState(pos, Blocks.SPAWNER.defaultBlockState(), false);
            chunk.setBlockEntity(guardianSpawner(pos, post.index()));
        }
    }

    public static net.minecraft.world.level.block.entity.SpawnerBlockEntity guardianSpawner(BlockPos pos, int index) {
        var block = new net.minecraft.world.level.block.entity.SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
        String id = switch (index) { case 0 -> "minecraft:ghast"; case 1 -> "minecraft:phantom"; case 2 -> "minecraft:blaze"; default -> throw new IllegalArgumentException("Unknown Solar guardian"); };
        var entity = new net.minecraft.nbt.CompoundTag();
        entity.putString("id", id);
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putBoolean(GUARDIAN, true);
        entity.put("NeoForgeData", marker);
        var spawn = new net.minecraft.nbt.CompoundTag();
        spawn.put("entity", entity);
        // Explicit custom rules bypass Nether, ground and light restrictions, retaining collision and Peaceful checks.
        spawn.put("custom_spawn_rules", new net.minecraft.nbt.CompoundTag());
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.put("SpawnData", spawn);
        tag.putShort("Delay", (short) 100);
        tag.putShort("MinSpawnDelay", (short) 600);
        tag.putShort("MaxSpawnDelay", (short) 1200);
        tag.putShort("SpawnCount", (short) 1);
        tag.putShort("MaxNearbyEntities", (short) (index == 0 ? 2 : 3));
        tag.putShort("RequiredPlayerRange", (short) 96);
        tag.putShort("SpawnRange", (short) 8);
        block.getSpawner().load(null, pos, tag);
        block.getPersistentData().putBoolean(GUARDIAN, true);
        block.getPersistentData().putInt("guardian_index", index);
        return block;
    }

    /** Returns immediately for ordinary spawners and generators, before any Solar geometry lookup. */
    public static boolean pauseGuardianSpawner(ServerLevel level, BlockPos pos, net.minecraft.world.level.BaseSpawner spawner) {
        if (!(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return false;
        var owner = spawner.getOwner();
        if (owner == null || owner.left().isEmpty() || !owner.left().get().getPersistentData().getBoolean(GUARDIAN)) return false;
        var config = generator.solarSettings().guardians();
        if (!config.enabled() || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return true;
        if (!level.hasNearbyAlivePlayer(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 96)) return true;
        for (int x = Math.floorDiv(pos.getX() - 12, 16); x <= Math.floorDiv(pos.getX() + 12, 16); x++) {
            for (int z = Math.floorDiv(pos.getZ() - 12, 16); z <= Math.floorDiv(pos.getZ() + 12, 16); z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return true;
            }
        }
        int index = owner.left().get().getPersistentData().getInt("guardian_index");
        if (index == 1 && !level.isNight()) return true;
        var type = switch (index) { case 0 -> EntityType.GHAST; case 1 -> EntityType.PHANTOM; default -> EntityType.BLAZE; };
        var center = generator.layout().systemAtBlock(pos.getX(), pos.getZ()).central();
        double extent = center.radius() + 160;
        var area = new AABB(center.x() - extent, level.getMinBuildHeight(), center.z() - extent,
                center.x() + extent, level.getMaxBuildHeight(), center.z() + extent);
        return level.getEntitiesOfClass(Mob.class, area, mob -> mob.getType() == type).size() >= (index == 0 ? 2 : 3);
    }

    @SubscribeEvent public static void protectShell(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        var source = event.getExplosion().getIndirectSourceEntity();
        if (!(source instanceof Ghast) || !source.getPersistentData().getBoolean(GUARDIAN)) return;
        event.getAffectedBlocks().removeIf(pos -> {
            var body = generator.layout().bodyAt(pos.getX(), pos.getY(), pos.getZ(), 0);
            return body != null && body.central() && !body.contains(pos.getX(), pos.getY(), pos.getZ(), 2);
        });
    }
}
