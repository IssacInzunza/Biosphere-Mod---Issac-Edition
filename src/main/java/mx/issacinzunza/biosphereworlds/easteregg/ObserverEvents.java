package mx.issacinzunza.biosphereworlds.easteregg;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Server-thread encounter lifecycle. Runtime state belongs to its level, never to worldgen or a static world map. */
@EventBusSubscriber(modid = BiosphereWorlds.MODID)
public final class ObserverEvents {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BiosphereWorlds.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Runtime>> RUNTIME = ATTACHMENTS.register("observer_runtime",
            () -> AttachmentType.builder(Runtime::new).build());
    private static final String CLOCK = "biosphereworlds_observer";
    private static final ResourceKey<DamageType> PULSE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BiosphereWorlds.MODID, "observer_pulse"));

    private ObserverEvents() { }

    public static boolean applies(Level level) {
        if (!(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD) return false;
        var generator = server.getChunkSource().getGenerator();
        return BiosphereWorldgen.isBiosphereGenerator(generator) || generator instanceof SolarChunkGenerator;
    }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !applies(player.level())) return;
        if (!EasterEggConfig.ENABLED.get() || player.tickCount % 20 != 0
                || !player.isAlive() || player.isSpectator() || player.isSleeping()) return;
        var level = player.serverLevel();
        var runtime = level.getData(RUNTIME);
        var previous = runtime.motion.get(player.getUUID());
        Vec3 position = player.position();
        double speed = previous == null ? 0 : position.distanceTo(previous.position());
        int moving = previous != null && level.getGameTime() - previous.time() == 20
                && ObserverRules.movingInAir(speed, clearBelow(level, position)) ? previous.moving() + 1 : 0;
        runtime.motion.put(player.getUUID(), new Motion(position, level.getGameTime(), moving));
        var clock = clock(player, true);
        int quiet = Math.max(0, clock.getInt("quiet_ticks") - 20);
        clock.putInt("quiet_ticks", quiet);
        double remaining = Math.max(0, clock.getDouble("remaining") - 20 * ObserverRules.rate(
                level.getDayTime(), mountain(player), ObserverRules.halloween(LocalDate.now())));
        clock.putDouble("remaining", remaining);
        if (quiet > 0 || remaining > 0 || nearby(level, player.position())) return;
        var random = random(player, clock.getLong("sequence"));
        if (!spawn(player, 0, false, random)) {
            clock.putDouble("remaining", 1200); // Retry later; never load chunks to make room.
        }
    }

    @SubscribeEvent public static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level)) return;
        var runtime = level.getExistingDataOrNull(RUNTIME);
        if (runtime == null) return;
        var iterator = runtime.encounters.iterator();
        while (iterator.hasNext()) {
            var encounter = iterator.next();
            if (!EasterEggConfig.ENABLED.get() || encounter.lights.stream().anyMatch(ObserverEntity::isRemoved)
                    || encounter.leader().age() >= encounter.leader().duration()
                    || !encounter.keepWitness(level)) {
                iterator.remove(); // Remove override before notifying circuits to restore their current inputs.
                encounter.finish(level);
            } else {
                encounter.tick(level);
            }
        }
        if (level.getGameTime() % 100 == 0) {
            runtime.motion.entrySet().removeIf(e -> level.getGameTime() - e.getValue().time() > 100);
            runtime.launches.entrySet().removeIf(e -> level.getGameTime() - e.getValue().time() > 400);
        }
        if (runtime.encounters.isEmpty()) runtime.launches.clear();
        ObserverWireless.publish(level, runtime.encounters.stream().filter(e -> e.signal >= 0)
                .map(e -> e.effectAnchor.getCenter()).toList());
    }

    @SubscribeEvent public static void attacked(AttackEntityEvent event) {
        if (event.getTarget() instanceof ObserverEntity light && event.getEntity() instanceof ServerPlayer player
                && applies(player.level()) && EasterEggConfig.ENABLED.get())
            provoke(player.serverLevel(), light, player.damageSources().playerAttack(player));
    }

    @SubscribeEvent public static void projectileJoined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level) || !EasterEggConfig.ENABLED.get()
                || event.loadedFromDisk() || !(event.getEntity() instanceof Projectile projectile)) return;
        var runtime = level.getExistingDataOrNull(RUNTIME);
        if (runtime == null || runtime.encounters.isEmpty() || runtime.launches.size() >= 512) return;
        if (runtime.encounters.stream().noneMatch(e -> e.leader().distanceToSqr(projectile) <= 1024 * 1024)) return;
        runtime.launches.put(projectile.getUUID(), new Launch(projectile.position(), level.getGameTime(), emplacement(level, projectile)));
    }

    private static boolean emplacement(ServerLevel level, Projectile projectile) {
        var owner = projectile.getOwner();
        if (owner != null && owner.getRootVehicle() != owner) return true;
        if (!level.getEntities(projectile, projectile.getBoundingBox().inflate(8), e -> !(e instanceof LivingEntity)
                && !(e instanceof Projectile) && !(e instanceof ObserverEntity) && e.getBoundingBox().getSize() > 2).isEmpty()) return true;
        Vec3 reverse = projectile.getDeltaMovement().normalize().scale(-1);
        for (int back = 1; back <= 3; back++) {
            var pos = BlockPos.containing(projectile.position().add(reverse.scale(back)));
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntities().containsKey(pos)) return true;
        }
        return false;
    }

    @SubscribeEvent public static void projectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof ObserverEntity light
                && light.level() instanceof ServerLevel level) provokeProjectile(level, light, event.getProjectile());
    }

    @SubscribeEvent public static void projectileExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !applies(level) || !EasterEggConfig.ENABLED.get()
                || !(event.getExplosion().getDirectSourceEntity() instanceof Projectile projectile)) return;
        for (var entity : event.getAffectedEntities())
            if (entity instanceof ObserverEntity light) provokeProjectile(level, light, projectile);
    }

    /** Observe swept flight even when a mod's custom damage source does not carry its projectile. */
    @SubscribeEvent public static void projectileTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.level() instanceof ServerLevel level)
                || !applies(level) || !EasterEggConfig.ENABLED.get()) return;
        var runtime = level.getExistingDataOrNull(RUNTIME);
        if (runtime == null || !runtime.launches.containsKey(projectile.getUUID())) return;
        Vec3 from = projectile.position(), to = from.add(projectile.getDeltaMovement());
        if (from.distanceToSqr(to) > 512 * 512) return;
        for (var encounter : runtime.encounters) {
            if (encounter.retaliationUsed) continue;
            for (var light : encounter.lights) {
                if (light.getBoundingBox().inflate(2).contains(from)
                        || light.getBoundingBox().inflate(2).clip(from, to).isPresent()) {
                    provokeProjectile(level, light, projectile);
                    break;
                }
            }
        }
    }

    public static boolean provoke(ServerLevel level, ObserverEntity light, DamageSource source) {
        if (!applies(level) || !level.getServer().isSameThread() || !EasterEggConfig.ENABLED.get()) return false;
        if (source.getDirectEntity() instanceof Projectile projectile) return provokeProjectile(level, light, projectile);
        return source.getEntity() instanceof ServerPlayer player && retaliate(level, light, player, null);
    }

    private static boolean provokeProjectile(ServerLevel level, ObserverEntity light, Projectile projectile) {
        if (!applies(level) || !level.getServer().isSameThread() || !EasterEggConfig.ENABLED.get()) return false;
        var runtime = level.getExistingDataOrNull(RUNTIME);
        if (runtime == null) return false;
        var launch = runtime.launches.get(projectile.getUUID());
        if (launch != null && (launch.emplacement() || projectile.getOwner() == null))
            return retaliate(level, light, null, launch.origin());
        return projectile.getOwner() instanceof ServerPlayer player && retaliate(level, light, player, null);
    }

    private static boolean retaliate(ServerLevel level, ObserverEntity light, ServerPlayer aggressor, Vec3 battery) {
        if (light.isRemoved()) return false;
        if (aggressor != null && (!aggressor.isAlive() || aggressor.isSpectator() || aggressor.level() != level)) return false;
        var runtime = level.getExistingDataOrNull(RUNTIME);
        if (runtime == null) return false;
        for (var encounter : runtime.encounters) if (encounter.lights.contains(light)) {
            encounter.retaliate(level, aggressor, battery);
            return true;
        }
        return false;
    }

    private record Motion(Vec3 position, long time, int moving) { }

    private static int clearBelow(ServerLevel level, Vec3 point) {
        for (int depth = 1; depth <= 12; depth++) {
            var pos = BlockPos.containing(point).below(depth);
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !chunk.getBlockState(pos).isAir()) return depth - 1;
        }
        return 12;
    }

    private static boolean flying(ServerPlayer player) {
        if (player.isFallFlying() || player.getAbilities().flying) return true;
        var runtime = player.serverLevel().getExistingDataOrNull(RUNTIME);
        var motion = runtime == null ? null : runtime.motion.get(player.getUUID());
        return motion != null && motion.moving() >= 2;
    }

    private static boolean mountain(ServerPlayer player) { return player.level().getBiome(player.blockPosition()).is(BiomeTags.IS_MOUNTAIN); }

    private static Random random(ServerPlayer player, long sequence) {
        UUID id = player.getUUID();
        return new Random(player.serverLevel().getSeed() ^ id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 19)
                ^ sequence * 0x6A09E667F3BCC909L ^ 0x4F42534552564552L);
    }

    private static CompoundTag clock(ServerPlayer player, boolean initialize) {
        var root = player.getPersistentData();
        var persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        var clock = persisted.getCompound(CLOCK);
        if (initialize && !clock.contains("remaining")) {
            resetClock(player, clock);
            persisted.put(CLOCK, clock);
            root.put(Player.PERSISTED_NBT_TAG, persisted);
        }
        return clock;
    }

    private static void resetClock(ServerPlayer player, CompoundTag clock) {
        long sequence = clock.getLong("sequence") + 1;
        clock.putLong("sequence", sequence);
        clock.putDouble("remaining", ObserverRules.sampleTicks(random(player, sequence),
                ObserverRules.MIN_INTERVAL_SECONDS, ObserverRules.MAX_INTERVAL_SECONDS));
        clock.putInt("quiet_ticks", ObserverRules.QUIET_TICKS);
    }

    private static boolean nearby(ServerLevel level, Vec3 position) {
        var runtime = level.getExistingDataOrNull(RUNTIME);
        return runtime != null && runtime.encounters.stream().anyMatch(e -> e.near(position));
    }

    public static ServerPlayer approachingWitness(ServerLevel level, Vec3 point, boolean interior) {
        if (!applies(level)) return null;
        double radius = (interior ? ObserverRules.INTERIOR_CLEARANCE : ObserverRules.EXTERIOR_CLEARANCE) + 16;
        return level.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.getEyePosition().distanceToSqr(point) < radius * radius)
                .min(java.util.Comparator.comparingDouble(p -> p.getEyePosition().distanceToSqr(point))).orElse(null);
    }

    /** Excludes every player and generic mounted/large vehicle; no transport mod IDs or physics calls. */
    public static boolean clearOfWitnesses(ServerLevel level, Vec3 from, Vec3 to, boolean interior) {
        if (!applies(level)) return false;
        int radius = interior ? ObserverRules.INTERIOR_CLEARANCE : ObserverRules.EXTERIOR_CLEARANCE;
        Vec3 direction = to.subtract(from);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double t = direction.lengthSqr() == 0 ? 0 : Math.clamp(player.getEyePosition().subtract(from).dot(direction) / direction.lengthSqr(), 0, 1);
            if (from.add(direction.scale(t)).distanceToSqr(player.getEyePosition()) < radius * radius) return false;
            var vehicle = player.getRootVehicle();
            if (vehicle != player && vehicle.getBoundingBox().inflate(16).intersects(new AABB(from, to).inflate(2))) return false;
        }
        var bounds = new AABB(from, to).inflate(2);
        return level.getEntities((Entity) null, bounds.inflate(16), e -> !(e instanceof ObserverEntity) && !(e instanceof Projectile)
                && (!e.getPassengers().isEmpty() || e.getBoundingBox().getSize() > 4)).stream()
                .noneMatch(e -> e.getBoundingBox().inflate(16).intersects(bounds));
    }

    /** Checks geometry only through its owner, and blocks only after confirming their chunks are loaded. */
    public static boolean freeSpace(ServerLevel level, Vec3 point) {
        return freeSpace(level, point, false);
    }

    public static boolean freeSpace(ServerLevel level, Vec3 point, boolean interior) {
        if (!applies(level)) return false;
        BlockPos pos = BlockPos.containing(point);
        // Entities may fly above build height; loaded chunks return air outside their block sections.
        if (pos.getY() < level.getMinBuildHeight() + 2 || !level.getWorldBorder().isWithinBounds(pos)) return false;
        for (BlockPos corner : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            var chunk = level.getChunkSource().getChunkNow(corner.getX() >> 4, corner.getZ() >> 4);
            if (chunk == null || !chunk.getBlockState(corner).isAir()) return false;
        }
        var generator = level.getChunkSource().getGenerator();
        if (interior) return inside(level, point);
        if (generator instanceof SolarChunkGenerator solar) {
            var layout = solar.layout();
            return layout.bodyAt(point.x, point.y, point.z, -3) == null
                    && layout.systemAtBlock(point.x, point.z).moons().stream().noneMatch(m -> m.contains(point.x, point.y, point.z));
        }
        var layout = BiosphereWorldgen.classicLayout(generator);
        return !layout.isInside(point.x, point.y, point.z, -3) && !layout.isInsideSatellite(point.x, point.y, point.z);
    }

    private static boolean inside(ServerLevel level, Vec3 point) {
        var generator = level.getChunkSource().getGenerator();
        return generator instanceof SolarChunkGenerator solar
                ? solar.layout().bodyAt(point.x, point.y, point.z, 4) != null
                : BiosphereWorldgen.classicLayout(generator).distanceToEdge(point.x, point.y, point.z) > 4;
    }

    /** Swept checks stop fast lights at thin walls and glass, without loading chunks. */
    public static boolean freePath(ServerLevel level, Vec3 from, Vec3 to, boolean interior) {
        if (!applies(level)) return false;
        double distance = from.distanceTo(to);
        if (distance > 8) return false;
        int steps = Math.max(1, (int) Math.ceil(distance * 2));
        for (int step = 1; step <= steps; step++)
            if (!freeSpace(level, from.lerp(to, (double) step / steps), interior)) return false;
        return true;
    }

    private record Shot(Vec3 end, boolean reachesTarget, Entity struck) { }

    /** Real ignition follows the clipped impact, never a target behind its obstruction. */
    private static void chargeEffects(ServerLevel level, ObserverEntity light, Shot shot) {
        if (!applies(level) || !EasterEggConfig.ENABLED.get()) return;
        Vec3 end = shot.end();
        if (shot.struck() != null && !shot.struck().fireImmune() && shot.struck().getRemainingFireTicks() < 40)
            shot.struck().igniteForSeconds(8);
        // Fire's placement/portal checks can inspect nearby blocks. Do not ask the
        // engine to place it near an unloaded boundary, or search through a floor.
        if (loadedAround(level, end, 32)) {
            var impact = BlockPos.containing(end.subtract(end.subtract(light.position()).normalize().scale(0.1)));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int down = 0; down <= 4; down++) {
                var pos = impact.offset(x, -down, z);
                if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir()) break;
                var fire = BaseFireBlock.getState(level, pos);
                if (fire.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, fire);
                    break;
                }
            }
        }
        for (var player : level.players()) if (player.distanceToSqr(end) <= 512 * 512) {
            level.sendParticles(player, ParticleTypes.FLAME, true, end.x, end.y, end.z, 12, 0.8, 0.8, 0.8, 0.04);
            level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, end.x, end.y, end.z, 3, 0.5, 0.5, 0.5, 0.02);
        }
        if (light.age() % 20 == 0)
            level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2, 0.65f);
    }

    private static boolean loadedAround(ServerLevel level, Vec3 center, int radius) {
        for (int x = ((int) Math.floor(center.x) - radius) >> 4; x <= ((int) Math.floor(center.x) + radius) >> 4; x++)
            for (int z = ((int) Math.floor(center.z) - radius) >> 4; z <= ((int) Math.floor(center.z) + radius) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    private static boolean detonate(ServerLevel level, ObserverEntity light, Vec3 end) {
        if (!applies(level) || !EasterEggConfig.ENABLED.get()) return false;
        // Keep the center just before the impact surface. An explosion exactly at a
        // player's eyes skips that player in vanilla's zero-length knockback vector branch.
        Vec3 center = end.subtract(end.subtract(light.position()).normalize().scale(0.25));
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return false;
        int radius = ObserverRules.BLAST_RADIUS;
        // Vanilla power 8 has a 16-block entity radius; its block rays are shorter.
        // Burning debris can place fire near the edge; allow the vanilla fire/portal
        // placement queries their own loaded margin as well as the explosion rays.
        if (!loadedAround(level, center, radius + 24)) return false;
        level.explode(light, null, new ExplosionDamageCalculator() {
            @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
                return pos.getCenter().distanceToSqr(center) <= radius * radius
                        && super.shouldBlockExplode(explosion, reader, pos, state, power);
            }
        }, center, radius / 2.0f, true, Level.ExplosionInteraction.BLOCK);
        return true;
    }

    /** Explicitly checks loaded chunks before a raycast, including shots beyond vanilla's 128-block sight cap. */
    private static Shot traceShot(ServerLevel level, ObserverEntity light, Vec3 target, Player victim, int range) {
        if (!applies(level) || !EasterEggConfig.ENABLED.get()) return null;
        Vec3 from = light.position();
        double distance = from.distanceTo(target);
        int clearance = light.interior() ? ObserverRules.INTERIOR_CLEARANCE : ObserverRules.EXTERIOR_CLEARANCE;
        if (!Double.isFinite(distance) || distance < clearance || distance > range) return null;
        int steps = Math.max(1, (int) Math.ceil(distance / 8));
        for (int step = 0; step <= steps; step++) {
            var pos = BlockPos.containing(from.lerp(target, (double) step / steps));
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        }
        var hit = level.clip(new ClipContext(from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, light));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? target : hit.getLocation();
        if (end.distanceToSqr(from) > (double) range * range + 4) return null;
        boolean reaches = hit.getType() == HitResult.Type.MISS;
        Entity struck = reaches ? victim : null;
        for (var entity : level.getEntities(light, new AABB(from, end).inflate(1), e -> e != victim
                && !(e instanceof ObserverEntity) && !(e instanceof Projectile)
                && (e instanceof Player || !e.getPassengers().isEmpty() || e.getBoundingBox().getSize() > 4))) {
            var obstruction = entity.getBoundingBox().inflate(0.5).clip(from, end);
            if (obstruction.isPresent() && obstruction.get().distanceToSqr(from) < end.distanceToSqr(from)) {
                end = obstruction.get();
                reaches = false;
                struck = entity;
            }
        }
        if (victim != null && victim.getRootVehicle() != victim) {
            var obstruction = victim.getRootVehicle().getBoundingBox().inflate(0.5).clip(from, end);
            if (obstruction.isPresent() && obstruction.get().distanceToSqr(from) < end.distanceToSqr(from)) {
                end = obstruction.get();
                reaches = false;
                struck = victim.getRootVehicle();
            }
        }
        return new Shot(end, reaches, struck);
    }

    private static double shellTop(ServerLevel level, Vec3 player) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof SolarChunkGenerator solar) {
            var body = solar.layout().bodyAt(player.x, player.y, player.z, -3);
            return body == null ? player.y : body.y() + body.radius();
        }
        var sphere = BiosphereWorldgen.classicLayout(generator).sphereContaining(player.x, player.z);
        return sphere.centerY() + sphere.radius();
    }

    private static boolean spawn(ServerPlayer player, int count, boolean forceInterference, Random random) {
        return spawn(player, count, forceInterference, random, null, null);
    }

    private static boolean spawn(ServerPlayer player, int count, boolean forceInterference, Random random,
                                 ObserverRules.Mode forcedMode, Boolean forceInterior) {
        var level = player.serverLevel();
        if (!applies(level) || !EasterEggConfig.ENABLED.get() || nearby(level, player.position())) return false;
        var mode = forcedMode == null ? ObserverRules.mode(random.nextInt(100), flying(player)) : forcedMode;
        boolean interior = forceInterior == null ? random.nextInt(100) < 15 && inside(level, player.position())
                && mode != ObserverRules.Mode.GLASS : forceInterior;
        if (interior && !inside(level, player.position())) return false;
        if (count == 0) count = ObserverRules.lightCount(random.nextInt(100));
        if (interior) count = Math.min(3, count);
        int duration = ObserverRules.sampleTicks(random, ObserverRules.MIN_DURATION_SECONDS, ObserverRules.MAX_DURATION_SECONDS);
        long seed = random.nextLong();
        for (int attempt = 0; attempt < 24; attempt++) {
            double bearing = random.nextDouble() * Math.PI * 2;
            double distance = interior ? 88 + random.nextDouble() * 32 : 80 + random.nextDouble() * 80;
            double height = interior ? player.getEyeY() + 48 + random.nextDouble() * 16
                    : Math.max(player.getY() + 80 + random.nextDouble() * 80, shellTop(level, player.position()) + 48);
            var origin = new Vec3(player.getX() + Math.cos(bearing) * distance, height, player.getZ() + Math.sin(bearing) * distance);
            if ((mode == ObserverRules.Mode.FLYBY || mode == ObserverRules.Mode.ATTACK)
                    && (interior || !inside(level, player.position()))) origin = player.getEyePosition();
            var lights = new ArrayList<ObserverEntity>();
            boolean fits = true;
            for (int index = 0; index < count && fits; index++) {
                var light = new ObserverEntity(ObserverEntity.TYPE.get(), level);
                light.configure(origin, bearing + Math.PI / 2, seed, index, count, duration, player.getUUID(), mode, interior);
                double elevation = light.getY() - player.getEyeY();
                if (approachingWitness(level, light.position(), interior) != null || interior && (elevation < 48 || elevation > 64)) {
                    fits = false;
                    break;
                }
                // Reactive lights validate their starting space; subsequent swept checks follow the moving player.
                if (mode == ObserverRules.Mode.GLASS || mode == ObserverRules.Mode.FLYBY || mode == ObserverRules.Mode.ATTACK)
                    fits = freeSpace(level, light.position(), interior) && clearOfWitnesses(level, light.position(), light.position(), interior);
                else {
                    for (int age = 0; age < duration && fits; age += 10) fits = freeSpace(level, light.positionAt(age), interior)
                            && clearOfWitnesses(level, light.positionAt(age), light.positionAt(age), interior);
                    if (fits) fits = freeSpace(level, light.positionAt(duration - 1), interior)
                            && clearOfWitnesses(level, light.positionAt(duration - 1), light.positionAt(duration - 1), interior);
                }
                lights.add(light);
            }
            if (!fits) continue;
            for (var light : lights) {
                if (!level.addFreshEntity(light)) {
                    lights.forEach(ObserverEntity::discard);
                    return false;
                }
            }
            int budget = forceInterference ? ObserverRules.MAX_DRAIN_FE
                    : random.nextBoolean() ? random.nextInt(1, ObserverRules.MAX_DRAIN_FE + 1) : 0;
            var encounter = new Encounter(player.getUUID(), player.blockPosition(), List.copyOf(lights), budget);
            level.getData(RUNTIME).encounters.add(encounter);
            // Nearby players share the sighting; do not spawn another for the next player in the same group.
            for (var witness : level.players()) {
                if (witness.distanceToSqr(player) < (double) ObserverRules.SEPARATION * ObserverRules.SEPARATION) {
                    encounter.witnesses.add(witness.getUUID());
                    resetClock(witness, clock(witness, true));
                }
            }
            return true;
        }
        return false;
    }

    /** Called only by the signal mixin. Absent runtime data is an allocation-free vanilla path. */
    public static int signalOverride(Level level, BlockPos pos) {
        if (!applies(level) || !level.getServer().isSameThread()
                || !EasterEggConfig.ENABLED.get()) return -1;
        var runtime = level.getExistingDataOrNull(RUNTIME);
        if (runtime == null) return -1;
        for (var encounter : runtime.encounters) {
            if (encounter.signal >= 0 && encounter.sources.contains(pos)) return encounter.signal;
        }
        return -1;
    }

    public static final class Runtime {
        private final List<Encounter> encounters = new ArrayList<>();
        private final Map<UUID, Motion> motion = new HashMap<>();
        private final Map<UUID, Launch> launches = new HashMap<>();
    }

    private record Launch(Vec3 origin, long time, boolean emplacement) { }

    private static final class Encounter {
        private UUID owner;
        private final BlockPos anchor;
        private BlockPos effectAnchor;
        private final Set<UUID> witnesses = new HashSet<>();
        private final List<ObserverEntity> lights;
        private final ObserverRules.DrainBudget budget;
        private final Set<BlockPos> sources = new HashSet<>();
        private final Set<BlockPos> linkPositions = new HashSet<>();
        private final List<BlockPos> sections = new ArrayList<>();
        private int sectionIndex;
        private int blockIndex;
        private int signal = -1;
        private boolean scanned;
        private boolean attackAttempted;
        private boolean retaliationUsed;
        private int hostileUntil;
        private UUID firingTarget;
        private Vec3 battery;
        private int fireAt = -1;
        private int shots;
        private final List<BlockPos> machines = new ArrayList<>();
        private final List<ObserverLinks.Target> links = new ArrayList<>();

        private Encounter(UUID owner, BlockPos anchor, List<ObserverEntity> lights, int budget) {
            this.owner = owner;
            this.anchor = anchor.immutable();
            this.effectAnchor = this.anchor;
            this.witnesses.add(owner);
            this.lights = lights;
            this.budget = new ObserverRules.DrainBudget(budget);
        }

        private ObserverEntity leader() { return lights.getFirst(); }

        private boolean near(Vec3 position) {
            double radius = (double) ObserverRules.SEPARATION * ObserverRules.SEPARATION;
            return anchor.getCenter().distanceToSqr(position) < radius || leader().position().distanceToSqr(position) < radius;
        }

        private boolean keepWitness(ServerLevel level) {
            for (var player : level.players()) if (player.isAlive() && !player.isSpectator() && near(player.position())
                    && witnesses.add(player.getUUID())) resetClock(player, clock(player, true));
            var current = level.getPlayerByUUID(owner);
            if (current != null && current.isAlive() && !current.isSpectator() && current.distanceToSqr(leader()) <= 1024 * 1024) return true;
            var next = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && near(p.position()))
                    .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(leader()))).orElse(null);
            if (next == null) return false;
            owner = next.getUUID();
            lights.forEach(light -> light.observe(owner));
            return true;
        }

        private void retaliate(ServerLevel level, ServerPlayer aggressor, Vec3 origin) {
            if (retaliationUsed) return;
            retaliationUsed = true;
            hostileUntil = leader().age() + ObserverRules.HOSTILE_TICKS;
            firingTarget = aggressor == null ? null : aggressor.getUUID();
            battery = origin;
            shots = origin == null ? 1 : 3;
            fireAt = leader().age() + ObserverRules.RETALIATION_CHARGE_TICKS;
            attackAttempted = true; // A pending natural pulse must not be redirected onto a bystander later.
            lights.forEach(light -> { light.hostile(true); light.warnUntil(fireAt); light.lingerUntil(hostileUntil); });
            if (origin != null && origin.distanceToSqr(leader().position()) <= 384 * 384
                    && level.getChunkSource().getChunkNow((int) Math.floor(origin.x) >> 4, (int) Math.floor(origin.z) >> 4) != null) {
                signal = -1;
                notifySources(level);
                for (var link : links) link.update(level, -1);
                sources.clear(); linkPositions.clear(); sections.clear(); machines.clear(); links.clear();
                sectionIndex = blockIndex = 0;
                scanned = false;
                effectAnchor = BlockPos.containing(origin);
            }
        }

        private void scan(ServerLevel level) {
            var anchor = effectAnchor;
            int radius = ObserverRules.INTERFERENCE_RADIUS;
            var blocks = new ArrayList<net.minecraft.world.level.block.entity.BlockEntity>();
            int minSection = Math.max(level.getMinSection(), (anchor.getY() - radius) >> 4);
            int maxSection = Math.min(level.getMaxSection() - 1, (anchor.getY() + radius) >> 4);
            for (int x = (anchor.getX() - radius) >> 4; x <= (anchor.getX() + radius) >> 4; x++) {
                for (int z = (anchor.getZ() - radius) >> 4; z <= (anchor.getZ() + radius) >> 4; z++) {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (var block : chunk.getBlockEntities().values()) {
                        if (!block.isRemoved() && block.getBlockPos().closerThan(anchor, radius + 0.5)) blocks.add(block);
                    }
                    for (int y = minSection; y <= maxSection; y++) {
                        if (!chunk.getSection(level.getSectionIndexFromSectionY(y)).hasOnlyAir())
                            sections.add(new BlockPos(x << 4, y << 4, z << 4));
                    }
                }
            }
            blocks.sort(java.util.Comparator.comparingDouble(b -> b.getBlockPos().distSqr(anchor)));
            for (var block : blocks) {
                var pos = block.getBlockPos();
                var link = links.size() < 64 ? ObserverLinks.find(level, pos) : null;
                if (link != null) {
                    links.add(link);
                    linkPositions.add(pos.immutable());
                }
                if (budget.remaining() > 0 && machines.size() < 256) machines.add(pos.immutable());
            }
            sections.sort(java.util.Comparator.comparingDouble(p -> p.offset(8, 8, 8).distSqr(anchor)));
            scanned = true;
        }

        /** Bounded, nearest-first search; empty/unloaded sections are skipped without forcing chunks. */
        private boolean scanSources(ServerLevel level) {
            var anchor = effectAnchor;
            int examined = 0;
            boolean added = false;
            var pos = new BlockPos.MutableBlockPos();
            while (sectionIndex < sections.size() && sources.size() < 64 && examined < ObserverRules.BLOCK_SCAN_BUDGET) {
                var origin = sections.get(sectionIndex);
                var chunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
                var section = chunk == null ? null : chunk.getSection(level.getSectionIndexFromSectionY(origin.getY() >> 4));
                if (section == null || section.hasOnlyAir()) {
                    sectionIndex++;
                    blockIndex = 0;
                    continue;
                }
                while (blockIndex < 4096 && sources.size() < 64 && examined < ObserverRules.BLOCK_SCAN_BUDGET) {
                    int index = blockIndex++;
                    examined++;
                    int x = index & 15, y = index >> 8, z = (index >> 4) & 15;
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (pos.closerThan(anchor, ObserverRules.INTERFERENCE_RADIUS + 0.5)
                            && !linkPositions.contains(pos) && section.getBlockState(x, y, z).isSignalSource())
                        added |= sources.add(pos.immutable());
                }
                if (blockIndex == 4096) {
                    sectionIndex++;
                    blockIndex = 0;
                }
            }
            return added;
        }

        private void tick(ServerLevel level) {
            if (!scanned) scan(level);
            boolean added = scanSources(level);
            int nextSignal = ObserverRules.redstonePulse(leader().age(), leader().encounterSeed());
            boolean hostile = leader().age() < hostileUntil;
            lights.forEach(light -> light.hostile(hostile));
            if (hostile) nextSignal = leader().age() % 10 < 5 ? 15 : 0;
            if (nextSignal != signal || added && nextSignal >= 0) {
                signal = nextSignal;
                notifySources(level);
            }
            // Reapply while pulsing: a transmitter can update its receiver in the same tick.
            for (var link : links) link.update(level, nextSignal);
            if (!attackAttempted && leader().mode() == ObserverRules.Mode.ATTACK
                    && leader().age() >= leader().duration() * 0.35 && leader().age() < leader().duration() * 0.8) {
                var player = level.getPlayerByUUID(owner);
                if (player != null && traceShot(level, leader(), player.getEyePosition(), player, ObserverRules.SHOT_RANGE) != null) {
                    attackAttempted = true;
                    firingTarget = player.getUUID();
                    shots = 1;
                    fireAt = leader().age() + ObserverRules.WARNING_TICKS;
                    lights.forEach(light -> light.warnUntil(fireAt));
                }
            }
            if (shots > 0) {
                var player = firingTarget == null ? null : level.getPlayerByUUID(firingTarget);
                Vec3 target = battery != null ? battery.add((shots - 2) * 1.5, 0, 0)
                        : player != null && player.isAlive() && !player.isSpectator() ? player.getEyePosition() : null;
                Shot shot = target == null ? null : traceShot(level, leader(), target, player, battery == null ? ObserverRules.SHOT_RANGE : 384);
                if (retaliationUsed && leader().age() < fireAt) {
                    if (shot == null) leader().clearBeam();
                    else {
                        leader().pulse(shot.end());
                        if (leader().age() % 4 == 0) chargeEffects(level, leader(), shot);
                    }
                } else if (leader().age() >= fireAt) {
                    if (shot != null) {
                        leader().pulse(shot.end());
                        if (retaliationUsed) detonate(level, leader(), shot.end());
                        else if (battery == null && shot.reachesTarget() && player != null) {
                            player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                    .getHolderOrThrow(PULSE)), 2);
                        }
                    }
                    shots--;
                    fireAt = leader().age() + 8;
                }
            }
            if (leader().age() % 20 == 0 && budget.remaining() > 0 && !machines.isEmpty()) {
                int index = Math.floorMod(leader().age() / 20 + leader().encounterSeed(), machines.size());
                ObserverEnergy.drain(level, machines.get(index), budget);
            }
        }

        private void notifySources(ServerLevel level) {
            for (var pos : sources) {
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null) continue;
                // Do not directly notify unloaded neighbors. Further circuit propagation belongs to vanilla/the machine.
                boolean loaded = true;
                for (var direction : net.minecraft.core.Direction.values()) {
                    var neighbor = pos.relative(direction);
                    if (level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4) == null) {
                        loaded = false;
                        break;
                    }
                }
                if (loaded) level.updateNeighborsAt(pos, chunk.getBlockState(pos).getBlock());
            }
        }

        private void finish(ServerLevel level) {
            for (var link : links) link.update(level, -1);
            lights.forEach(ObserverEntity::discard);
            if (signal >= 0) notifySources(level);
        }
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var observer = Commands.literal("observer")
                .requires(source -> source.hasPermission(2))
                .executes(c -> debugSpawn(c.getSource(), 0, false))
                .then(Commands.argument("lights", IntegerArgumentType.integer(1, ObserverRules.MAX_LIGHTS))
                        .executes(c -> debugSpawn(c.getSource(), IntegerArgumentType.getInteger(c, "lights"), false)))
                .then(Commands.literal("interference").executes(c -> debugSpawn(c.getSource(), 0, true))
                        .then(Commands.argument("lights", IntegerArgumentType.integer(1, ObserverRules.MAX_LIGHTS))
                                .executes(c -> debugSpawn(c.getSource(), IntegerArgumentType.getInteger(c, "lights"), true))))
                .then(Commands.literal("stop").executes(c -> stop(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())));
        var inside = Commands.literal("inside").executes(c -> debugSpawn(c.getSource(), 0, false, null, true))
                .then(Commands.argument("lights", IntegerArgumentType.integer(1, 3))
                        .executes(c -> debugSpawn(c.getSource(), IntegerArgumentType.getInteger(c, "lights"), false, null, true)));
        for (var mode : ObserverRules.Mode.values()) {
            String name = mode.name().toLowerCase(java.util.Locale.ROOT);
            observer.then(Commands.literal(name).executes(c -> debugSpawn(c.getSource(), 0, false, mode, false))
                    .then(Commands.argument("lights", IntegerArgumentType.integer(1, ObserverRules.MAX_LIGHTS))
                            .executes(c -> debugSpawn(c.getSource(), IntegerArgumentType.getInteger(c, "lights"), false, mode, false))));
            inside.then(Commands.literal(name).executes(c -> debugSpawn(c.getSource(), 0, false, mode, true))
                    .then(Commands.argument("lights", IntegerArgumentType.integer(1, 3))
                            .executes(c -> debugSpawn(c.getSource(), IntegerArgumentType.getInteger(c, "lights"), false, mode, true))));
        }
        observer.then(inside);
        event.getDispatcher().register(Commands.literal("biospheres")
                .then(Commands.literal("debug").then(observer)));
    }

    private static int debugSpawn(CommandSourceStack source, int count, boolean interference) throws CommandSyntaxException {
        return debugSpawn(source, count, interference, null, null);
    }

    private static int debugSpawn(CommandSourceStack source, int count, boolean interference,
                                  ObserverRules.Mode mode, Boolean interior) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!applies(player.level())) return fail(source, "world");
        if (!EasterEggConfig.ENABLED.get()) return fail(source, "disabled");
        if (nearby(player.serverLevel(), player.position())) return fail(source, "nearby");
        if (!spawn(player, count, interference, new Random(), mode, interior)) return fail(source, "space");
        var encounter = player.level().getData(RUNTIME).encounters.getLast();
        var pos = encounter.leader().blockPosition();
        source.sendSuccess(() -> Component.translatable("biosphereworlds.observer.debug.spawned", encounter.lights.size(), pos.getX(), pos.getY(), pos.getZ())
                .append(" [" + encounter.leader().mode().name().toLowerCase(java.util.Locale.ROOT) + "]"), false);
        return encounter.lights.size();
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!applies(player.level())) return fail(source, "world");
        var runtime = player.level().getExistingDataOrNull(RUNTIME);
        int removed = 0;
        if (runtime != null) {
            var iterator = runtime.encounters.iterator();
            while (iterator.hasNext()) {
                var encounter = iterator.next();
                if (!encounter.witnesses.contains(player.getUUID()) && !encounter.near(player.position())) continue;
                iterator.remove();
                encounter.finish(player.serverLevel());
                removed++;
            }
        }
        int count = removed;
        if (runtime != null) ObserverWireless.publish(player.serverLevel(), runtime.encounters.stream()
                .filter(e -> e.signal >= 0).map(e -> e.effectAnchor.getCenter()).toList());
        source.sendSuccess(() -> Component.translatable("biosphereworlds.observer.debug.stopped", count), false);
        return count;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!applies(player.level())) return fail(source, "world");
        var clock = clock(player, false);
        var runtime = player.level().getExistingDataOrNull(RUNTIME);
        int lights = runtime == null ? 0 : runtime.encounters.stream().mapToInt(e -> e.lights.size()).sum();
        int budget = runtime == null ? 0 : runtime.encounters.stream().mapToInt(e -> e.budget.remaining()).sum();
        int links = runtime == null ? 0 : runtime.encounters.stream().mapToInt(e -> e.links.size()).sum();
        int encounters = runtime == null ? 0 : runtime.encounters.size();
        long nearby = runtime == null ? 0 : runtime.encounters.stream().filter(e -> e.near(player.position())).count();
        long hostile = runtime == null ? 0 : runtime.encounters.stream().filter(e -> e.leader().age() < e.hostileUntil).count();
        source.sendSuccess(() -> Component.translatable("biosphereworlds.observer.debug.status",
                EasterEggConfig.ENABLED.get(), Math.ceil(clock.getDouble("remaining") / 1200),
                Math.ceil(clock.getInt("quiet_ticks") / 1200.0), ObserverRules.timeWeight(player.level().getDayTime()),
                mountain(player), ObserverRules.halloween(LocalDate.now()), lights, budget, links, encounters, nearby, hostile), false);
        return lights;
    }

    private static int fail(CommandSourceStack source, String reason) {
        source.sendFailure(Component.translatable("biosphereworlds.observer.debug." + reason));
        return 0;
    }
}
