package mx.issacinzunza.biosphereworlds.worldgen.solar;

import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Owns Solar cage construction, combat limits and compatibility with older guardians.
 * Current cages share one policy; layout supplies positions and events only route calls.
 * No state is cached globally; current cages query populations only when ready.
 */
public final class SolarGuardians {
    public static final String MARKER = "biosphereworlds_solar_guardian";
    /** Spawn delays in server ticks (20 ticks per second). */
    public static final int MIN_DELAY = 160;
    public static final int MAX_DELAY = 240;
    public static final int LOCAL_LIMIT = 8;
    public static final int SYSTEM_LIMIT = 24;
    // Persistent policy revision, used to upgrade existing marked cages once.
    private static final String RULES = "guardian_rules";

    /** How the mixin should handle a tick; RETRY backs off for one second. */
    public enum TickAction { VANILLA, PAUSE, RETRY }

    private SolarGuardians() { }

    /**
     * Builds a cage without attaching it to a world. Modern cages can select any guardian
     * type; their entity tag contains only the id so vanilla still calls finalizeSpawn.
     *
     * @param pos block position of the cage
     * @param index stable site index; legacy indices are 0=ghast, 1=phantom, 2=blaze
     * @param modern whether to apply the surface-covering policy
     * @return configured block entity, ready to publish with its spawner block
     */
    public static SpawnerBlockEntity createSpawner(BlockPos pos, int index, boolean modern) {
        var block = new SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
        String id = modern ? (index % 2 == 0 ? "minecraft:ghast" : "minecraft:blaze")
                : switch (index) {
                    case 0 -> "minecraft:ghast";
                    case 1 -> "minecraft:phantom";
                    case 2 -> "minecraft:blaze";
                    default -> throw new IllegalArgumentException("Unknown legacy guardian");
                };
        var spawn = spawnData(id);
        if (!modern) {
            var marker = new CompoundTag();
            marker.putBoolean(MARKER, true);
            spawn.getCompound("entity").put("NeoForgeData", marker);
        }
        var tag = new CompoundTag();
        tag.put("SpawnData", spawn);
        tag.putShort("Delay", (short) (modern ? 40 : 100));
        tag.putShort("MinSpawnDelay", (short) (modern ? MIN_DELAY : 600));
        tag.putShort("MaxSpawnDelay", (short) (modern ? MAX_DELAY : 1200));
        tag.putShort("SpawnCount", (short) (modern ? 2 : 1));
        tag.putShort("MaxNearbyEntities", (short) (modern ? LOCAL_LIMIT : index == 0 ? 2 : 3));
        tag.putShort("RequiredPlayerRange", (short) (modern ? SolarLayout.GUARDIAN_ACTIVATION_RANGE : 96));
        tag.putShort("SpawnRange", (short) 8);
        block.getSpawner().load(null, pos, tag);
        block.getPersistentData().putBoolean(MARKER, true);
        block.getPersistentData().putInt("guardian_index", index);
        if (modern) block.getPersistentData().putInt(RULES, 3);
        return block;
    }

    private static CompoundTag spawnData(String id) {
        var entity = new CompoundTag();
        entity.putString("id", id);
        var spawn = new CompoundTag();
        spawn.put("entity", entity);
        spawn.put("custom_spawn_rules", new CompoundTag());
        return spawn;
    }

    private static BlockEntity owner(BaseSpawner spawner) {
        if (spawner == null || spawner.getOwner() == null) return null;
        var block = spawner.getOwner().left().orElse(null);
        return block != null && block.getPersistentData().getBoolean(MARKER) ? block : null;
    }

    /**
     * Evaluates only Solar-owned cages. Cooldown ticks do no geometry or population work.
     * Ready cages select ghasts/blazes by day and may also select phantoms at night.
     * Limits count marked guardians, so ordinary monsters cannot disable Solar defenses.
     *
     * @param level server world; other generators return immediately
     * @param pos cage block position
     * @param spawner vanilla spawner whose marked owner determines the policy
     * @param delay current vanilla spawn delay, in ticks
     * @return the action to apply before the vanilla server tick
     */
    public static TickAction beforeTick(ServerLevel level, BlockPos pos, BaseSpawner spawner, int delay) {
        if (!(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return TickAction.VANILLA;
        var block = owner(spawner);
        if (block == null) return TickAction.VANILLA;
        if (!generator.solarSettings().guardians().enabled() || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return TickAction.PAUSE;
        upgradeCage(level, pos, spawner);
        if (delay > 0) return TickAction.VANILLA;
        var player = nearestPlayer(level, pos);
        if (player == null || !spawnAreaLoaded(level, pos, 12)) return TickAction.RETRY;
        var nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), SolarGuardians::isGuardian);
        if (nearby.size() >= LOCAL_LIMIT) return TickAction.RETRY;
        var center = generator.layout().systemAtBlock(pos.getX(), pos.getZ()).central();
        double extent = center.radius() + 160;
        var area = new AABB(center.x() - extent, level.getMinBuildHeight(), center.z() - extent,
                center.x() + extent, level.getMaxBuildHeight(), center.z() + extent);
        int systemCount = level.getEntitiesOfClass(Mob.class, area, SolarGuardians::isGuardian).size();
        int budget = Math.min(2, Math.min(LOCAL_LIMIT - nearby.size(), SYSTEM_LIMIT - systemCount));
        if (budget <= 0) return TickAction.RETRY;
        int choice = level.random.nextInt(level.isNight() ? 3 : 2);
        String id = choice == 0 ? "minecraft:ghast" : choice == 1 ? "minecraft:blaze" : "minecraft:phantom";
        var tag = spawner.save(new CompoundTag());
        tag.remove("SpawnPotentials");
        tag.put("SpawnData", spawnData(id));
        tag.putShort("SpawnCount", (short) budget);
        spawner.load(level, pos, tag);
        return TickAction.VANILLA;
    }

    /**
     * Upgrades only an enabled Solar cage's saved settings; no blocks are replaced.
     * Called after world/ownership/config checks and once per old cage. Disabled
     * guardians never mutate cage NBT. Positions and ordinary vanilla cages stay intact.
     * @param level owning Solar server world
     * @param pos cage position
     * @param spawner spawner whose owner carries the Solar marker
     */
    private static void upgradeCage(ServerLevel level, BlockPos pos, BaseSpawner spawner) {
        var block = owner(spawner);
        if (block == null || block.getPersistentData().getInt(RULES) >= 3) return;
        var tag = spawner.save(new CompoundTag());
        tag.putShort("Delay", (short) Math.min(40, tag.getShort("Delay")));
        tag.putShort("MinSpawnDelay", (short) MIN_DELAY);
        tag.putShort("MaxSpawnDelay", (short) MAX_DELAY);
        tag.putShort("SpawnCount", (short) 2);
        tag.putShort("MaxNearbyEntities", (short) LOCAL_LIMIT);
        tag.putShort("RequiredPlayerRange", (short) SolarLayout.GUARDIAN_ACTIVATION_RANGE);
        tag.put("SpawnData", spawnData("minecraft:ghast"));
        tag.remove("SpawnPotentials");
        spawner.load(level, pos, tag);
        block.getPersistentData().putInt(RULES, 3);
        block.setChanged();
    }

    private static boolean isGuardian(Mob mob) { return mob.getPersistentData().getBoolean(MARKER); }

    private static Player nearestPlayer(ServerLevel level, BlockPos pos) {
        Player nearest = null;
        double best = (double) SolarLayout.GUARDIAN_ACTIVATION_RANGE * SolarLayout.GUARDIAN_ACTIVATION_RANGE;
        for (var player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double distance = player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            if (distance <= best) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }

    private static boolean spawnAreaLoaded(ServerLevel level, BlockPos pos, int radius) {
        for (int x = Math.floorDiv(pos.getX() - radius, 16); x <= Math.floorDiv(pos.getX() + radius, 16); x++) {
            for (int z = Math.floorDiv(pos.getZ() - radius, 16); z <= Math.floorDiv(pos.getZ() + radius, 16); z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    /**
     * Moves distant candidates into engagement range of the approaching player.
     * Ghasts/blazes use 16 blocks; phantoms use 12 because their target scan is narrow.
     * This also prevents immediate distance despawn. Vanilla obstruction checks run
     * afterward at the adjusted position; no mob is teleported after being spawned.
     * @param event position check already scoped to a Solar world
     */
    public static void adjustSpawn(MobSpawnEvent.PositionCheck event) {
        var block = owner(event.getSpawner());
        if (block == null || block.getPersistentData().getInt(RULES) < 2 || !(event.getLevel() instanceof ServerLevel level)) return;
        var player = nearestPlayer(level, block.getBlockPos());
        if (player == null) return;
        var mob = event.getEntity();
        double dx = player.getX() - mob.getX();
        double dy = player.getY() - mob.getY();
        double dz = player.getZ() - mob.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double engagementRange = mob.getType() == EntityType.PHANTOM ? 12 : 16;
        if (distance <= engagementRange) return;
        // Leave room for a small sideways spread so a pair of ghasts does not
        // collapse into the same collision box when a distant cage activates.
        double move = (distance - (engagementRange - 4)) / distance;
        double horizontal = Math.hypot(dx, dz);
        double sideways = (level.random.nextDouble() - .5) * 8;
        double tangentX = horizontal > 0 ? -dz / horizontal : 1;
        double tangentZ = horizontal > 0 ? dx / horizontal : 0;
        double y = Math.max(level.getMinBuildHeight() + 2, Math.min(level.getMaxBuildHeight() - 6, mob.getY() + dy * move));
        mob.moveTo(mob.getX() + dx * move + tangentX * sideways, y, mob.getZ() + dz * move + tangentZ * sideways, mob.getYRot(), mob.getXRot());
    }

    /**
     * Tags only mobs originating from a marked cage, after Solar scope is checked.
     * @param event finalization event retaining vanilla initialization
     */
    public static void markSpawn(FinalizeSpawnEvent event) {
        var source = event.getSpawner();
        if (source == null || source.left().isEmpty()) return;
        if (source.left().get().getPersistentData().getBoolean(MARKER)) event.getEntity().getPersistentData().putBoolean(MARKER, true);
    }

    /**
     * Only marked ghast guardians receive Solar shell explosion protection.
     * @param event explosion whose affected blocks may include the central shell
     */
    public static void protectShell(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        var source = event.getExplosion().getIndirectSourceEntity();
        if (!(source instanceof Ghast) || !source.getPersistentData().getBoolean(MARKER)) return;
        event.getAffectedBlocks().removeIf(pos -> {
            var body = generator.layout().bodyAt(pos.getX(), pos.getY(), pos.getZ(), 0);
            return body != null && body.central() && !body.contains(pos.getX(), pos.getY(), pos.getZ(), 2);
        });
    }
    /**
     * Places cages once during decoration, after volume trim; never repairs broken cages.
     * @param chunk chunk owned by the generation worker; writes stay within its bounds
     * @param generator bound Solar generator containing the saved guardian switch
     */
    public static void placeCages(ChunkAccess chunk, SolarChunkGenerator generator) {
        if (!generator.solarSettings().guardians().enabled()) return;
        var layout = generator.layout();
        var system = layout.systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
        var center = system.central();
        double reach = center.radius() + 27;
        double dx = Math.max(0, Math.abs(chunk.getPos().getMiddleBlockX() - center.x()) - 8);
        double dz = Math.max(0, Math.abs(chunk.getPos().getMiddleBlockZ() - center.z()) - 8);
        if (dx * dx + dz * dz > reach * reach) return;
        for (var post : layout.guardianPosts(system)) {
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    var floor = new BlockPos(post.x() + ox, post.y() - 1, post.z() + oz);
                    if (new net.minecraft.world.level.ChunkPos(floor).equals(chunk.getPos())) {
                        chunk.setBlockState(floor, Blocks.OBSIDIAN.defaultBlockState(), false);
                    }
                }
            }
            var pos = new BlockPos(post.x(), post.y(), post.z());
            if (!new net.minecraft.world.level.ChunkPos(pos).equals(chunk.getPos())) continue;
            chunk.setBlockState(pos, Blocks.SPAWNER.defaultBlockState(), false);
            chunk.setBlockEntity(createSpawner(pos, post.index(), layout.version() >= 5));
        }
    }

    /**
     * Old snapshots retain interval spawning; current worlds use cages.
     * @param event server tick; non-Solar worlds return before reading configuration
     */
    public static void tickLegacy(LevelTickEvent.Post event) {
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
            mob.getPersistentData().putBoolean(MARKER, true);
            return level.addFreshEntity(mob);
        }
        return false;
    }

}
