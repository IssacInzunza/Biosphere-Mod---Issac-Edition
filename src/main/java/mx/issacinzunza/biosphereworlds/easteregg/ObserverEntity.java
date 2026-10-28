package mx.issacinzunza.biosphereworlds.easteregg;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.UUID;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Each visible light is a real, tracked entity. Encounters are transient and never saved in chunks. */
public final class ObserverEntity extends Entity {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BiosphereWorlds.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<ObserverEntity>> TYPE = TYPES.register("observer",
            () -> EntityType.Builder.<ObserverEntity>of(ObserverEntity::new, MobCategory.MISC)
                    .sized(1.2f, 1.2f).noSave().noSummon().fireImmune().clientTrackingRange(64).updateInterval(2)
                    .build(BiosphereWorlds.MODID + ":observer"));

    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> SEED = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HOSTILE = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WARNING = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PULSE = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_X = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_Y = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_Z = SynchedEntityData.defineId(ObserverEntity.class, EntityDataSerializers.FLOAT);
    private Vec3 origin = Vec3.ZERO;
    private double heading;
    private UUID owner;
    private boolean interior;
    private int blockedAt = -1;
    private Vec3 retreatOrigin;
    private Vec3 retreatDirection;
    private Vec3 interpolationTarget = Vec3.ZERO;
    private int interpolationSteps;

    public ObserverEntity(EntityType<? extends ObserverEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setSilent(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(START, 0L);
        builder.define(SEED, 0L);
        builder.define(DURATION, 0);
        builder.define(INDEX, 0);
        builder.define(COUNT, 1);
        builder.define(MODE, ObserverRules.Mode.FORMATION.ordinal());
        builder.define(HOSTILE, false);
        builder.define(WARNING, -1);
        builder.define(PULSE, -1);
        builder.define(BEAM_X, 0f);
        builder.define(BEAM_Y, 0f);
        builder.define(BEAM_Z, 0f);
    }

    public void configure(Vec3 origin, double heading, long seed, int index, int count, int duration) {
        this.origin = origin;
        this.heading = heading;
        entityData.set(START, level().getGameTime());
        entityData.set(SEED, seed);
        entityData.set(DURATION, duration);
        entityData.set(INDEX, index);
        entityData.set(COUNT, count);
        setPos(positionAt(0));
    }

    public void configure(Vec3 origin, double heading, long seed, int index, int count, int duration,
                          UUID owner, ObserverRules.Mode mode, boolean interior) {
        entityData.set(MODE, mode.ordinal());
        this.owner = owner;
        this.interior = interior;
        configure(origin, heading, seed, index, count, duration);
    }

    public int age() { return (int) (level().getGameTime() - entityData.get(START)); }
    public int duration() { return entityData.get(DURATION); }
    public long encounterSeed() { return entityData.get(SEED); }
    public ObserverRules.Mode mode() { return ObserverRules.Mode.values()[entityData.get(MODE)]; }
    public boolean interior() { return interior; }
    public void observe(UUID witness) { owner = witness; }
    public void hostile(boolean hostile) { entityData.set(HOSTILE, hostile); }
    public boolean hostile() { return entityData.get(HOSTILE); }
    public boolean warning() { return age() < entityData.get(WARNING); }
    public void warnUntil(int age) { entityData.set(WARNING, age); }
    public void lingerUntil(int age) { if (duration() < age) entityData.set(DURATION, age); }
    public Vec3 beam(float partialTick) {
        return age() + partialTick < entityData.get(PULSE)
                ? new Vec3(entityData.get(BEAM_X), entityData.get(BEAM_Y), entityData.get(BEAM_Z)) : Vec3.ZERO;
    }
    public void pulse(Vec3 target) {
        Vec3 delta = target.subtract(position());
        entityData.set(BEAM_X, (float) delta.x);
        entityData.set(BEAM_Y, (float) delta.y);
        entityData.set(BEAM_Z, (float) delta.z);
        entityData.set(PULSE, age() + 4);
    }
    public float brightness(float partialTick) {
        double age = age() + partialTick;
        float fade = (float) Math.clamp(Math.min(age / 20, (duration() - age) / 30), 0, 1);
        float pulse = ObserverRules.brightness(mode(), age, entityData.get(INDEX), encounterSeed());
        if (warning()) pulse = ((int) age / 4) % 2 == 0 ? 1 : 0.2f;
        return fade * pulse;
    }

    public Vec3 positionAt(int age) {
        var offset = ObserverRules.offset(mode(), entityData.get(INDEX), entityData.get(COUNT), age, duration(), encounterSeed());
        if (hostile() && entityData.get(COUNT) > 1 && mode() != ObserverRules.Mode.FLYBY && mode() != ObserverRules.Mode.ATTACK) {
            double arc = entityData.get(INDEX) * Math.PI / Math.max(1, entityData.get(COUNT) - 1);
            offset = new ObserverRules.Offset(offset.x() * 0.75 + Math.cos(arc) * 6,
                    offset.y() + Math.sin(arc) * 4, offset.z() * 0.75);
        }
        double scale = interior ? 0.45 : 1;
        double sin = Math.sin(heading), cos = Math.cos(heading);
        return origin.add((offset.x() * cos - offset.z() * sin) * scale, offset.y() * scale,
                (offset.x() * sin + offset.z() * cos) * scale);
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel level) {
            // Gate before time, geometry or random calculations, including externally created entities.
            if (!ObserverEvents.applies(level) || !EasterEggConfig.ENABLED.get() || duration() <= 0 || age() >= duration()) {
                discard();
                return;
            }
            super.tick();
            if (!ObserverEvents.clearOfWitnesses(level, position(), position(), interior)) {
                discard(); // An approaching/teleporting player or vessel never gets a close inspection.
                return;
            }
            Vec3 next = positionAt(age());
            var player = owner == null ? null : level.getPlayerByUUID(owner);
            if (player != null && (mode() == ObserverRules.Mode.FLYBY || mode() == ObserverRules.Mode.ATTACK))
                next = player.getEyePosition().add(next.subtract(origin));
            var threat = ObserverEvents.approachingWitness(level, position(), interior);
            if (threat != null && retreatOrigin == null) {
                retreatOrigin = position();
                retreatDirection = position().subtract(threat.getEyePosition()).normalize();
                blockedAt = age() - 60;
                entityData.set(DURATION, Math.min(duration(), age() + 40));
            }
            if (retreatOrigin != null)
                next = retreatOrigin.add(retreatDirection.scale(Math.max(0, age() - blockedAt - 60) * 0.8));
            Vec3 step = next.subtract(position());
            double speed = mode() == ObserverRules.Mode.FLYBY ? 2 : 1.5;
            if (step.length() > speed) next = position().add(step.normalize().scale(speed));
            if (!ObserverEvents.freePath(level, position(), next, interior)
                    || !ObserverEvents.clearOfWitnesses(level, position(), next, interior)) {
                if (blockedAt < 0) blockedAt = age();
                if (age() - blockedAt >= 60 && retreatOrigin == null) {
                    retreatOrigin = position();
                    Vec3 away = player == null ? origin.subtract(position()) : position().subtract(player.getEyePosition());
                    retreatDirection = away.lengthSqr() < 0.01 ? new Vec3(0, 1, 0) : away.normalize();
                }
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            setDeltaMovement(next.subtract(position()));
            setPos(next);
        } else {
            super.tick();
            if (interpolationSteps > 0) {
                setPos(position().lerp(interpolationTarget, 1.0 / interpolationSteps));
                interpolationSteps--;
            }
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        return level() instanceof ServerLevel level && ObserverEvents.provoke(level, this, source);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationTarget = new Vec3(x, y, z);
        interpolationSteps = Math.max(1, steps);
    }

    // Detectable by scanners/ray queries, but neither solid nor pushable; no drops or mob AI.
    @Override public boolean isPickable() { return true; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().expandTowards(beam(0)).inflate(3); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 1024 * 1024; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
