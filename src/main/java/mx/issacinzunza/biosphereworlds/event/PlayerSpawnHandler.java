package mx.issacinzunza.biosphereworlds.event;

import mx.issacinzunza.biosphereworlds.util.SphereUtil;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.Predicate;
import java.util.stream.Collectors;

@EventBusSubscriber
public class PlayerSpawnHandler {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player entity = event.getEntity();
        Level level = entity.level();
        if (!isBiosphereWorld(level)) {
            return;
        }
        // Existing players may have deliberately logged out underwater.
        if (!(entity instanceof ServerPlayer player)
                || player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) != 0
                || player.getPersistentData().getBoolean("biosphereworlds_spawn_checked")) return;
        player.getPersistentData().putBoolean("biosphereworlds_spawn_checked", true);
        level.getServer().execute(() -> {
            if (entity.level() == level) teleportIntoSphere(level, entity);
        });
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player entity = event.getEntity();
        Level level = entity.level();
        if (!isBiosphereWorld(level)) {
            return;
        }
        if (event.isEndConquered()) return;
        if (entity instanceof ServerPlayer player && player.getRespawnPosition() != null
                && player.getRespawnDimension().equals(level.dimension())
                && (player.isRespawnForced()
                    || level.getBlockState(player.getRespawnPosition()).getBlock() instanceof BedBlock)
                && player.blockPosition().closerThan(player.getRespawnPosition(), 4)
                && hasEnoughSpace(level, player.blockPosition())) return;
        level.getServer().execute(() -> {
            if (entity.level() == level) teleportIntoSphere(level, entity);
        });
    }

    private static boolean isBiosphereWorld(Level level) {
        return level instanceof ServerLevel serverLevel
                && (BiosphereWorldgen.isBiosphereGenerator(serverLevel.getChunkSource().getGenerator())
                || serverLevel.getChunkSource().getGenerator() instanceof SolarChunkGenerator);
    }

    private static void teleportIntoSphere(Level level, Player entity) {
        BlockPos currentPos = entity.blockPosition();
        Predicate<BlockState> isShell;
        Predicate<BlockPos> inside;
        int radius;
        if (((ServerLevel) level).getChunkSource().getGenerator() instanceof SolarChunkGenerator solar) {
            var layout = solar.layout();
            var system = layout.systemAtBlock(currentPos.getX(), currentPos.getZ());
            var shells = system.bodies().stream().map(solar::shellState).map(BlockState::getBlock)
                    .collect(Collectors.toUnmodifiableSet());
            isShell = state -> shells.contains(state.getBlock());
            var body = layout.bodyAt(currentPos.getX(), currentPos.getY(), currentPos.getZ(), 0);
            if (body != null) {
                radius = body.radius();
                inside = pos -> body.contains(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 3);
            } else {
                var moon = system.moons().stream().filter(m -> m.containsSquaredDistance(m.squaredDistanceTo(
                        currentPos.getX(), currentPos.getY(), currentPos.getZ()))).findFirst().orElse(null);
                if (moon == null) return;
                radius = moon.sphere().radius();
                inside = pos -> moon.contains(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
        } else {
            var generator = ((ServerLevel) level).getChunkSource().getGenerator();
            var world = BiosphereWorldgen.classicWorld(generator);
            isShell = world == null ? SphereUtil::isSphereBlock : world::isShell;
            var layout = BiosphereWorldgen.classicLayout(generator);
            var sphere = layout.sphereContaining(currentPos.getX(), currentPos.getZ());
            radius = sphere.radius();
            inside = pos -> layout.spheresAt(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1).contains(sphere);
        }

        BlockPos targetPos = findValidPosition(level, currentPos, Math.min(radius, 64), isShell, inside);

        if (targetPos != null) {
            entity.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        }
    }

    private static BlockPos findValidPosition(Level level, BlockPos center, int radius, Predicate<BlockState> isShell,
                                              Predicate<BlockPos> inside) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        BlockPos water = null;
        // Concentric rings prefer nearby land; remember water only as a fallback.
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring
                            || dx * dx + dz * dz > radius * radius) continue;
                    for (int y = maxY; y >= minY; y--) {
                        BlockPos checkPos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        BlockState state = level.getBlockState(checkPos);
                        if (state.isAir() || isShell.test(state)) continue;
                        // Harmless vegetation must not hide the ground underneath.
                        if (state.getFluidState().isEmpty() && state.getCollisionShape(level, checkPos).isEmpty()
                                && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
                                && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.WITHER_ROSE)
                                && !state.is(Blocks.POWDER_SNOW)) continue;
                        // Inspect only the exposed surface, never a cave below it.
                        if (inside.test(checkPos) && inside.test(checkPos.above()) && inside.test(checkPos.above(2))
                                && hasEnoughSpace(level, checkPos.above())) {
                            if (isSolidBlock(state, level, checkPos) && !state.is(BlockTags.LEAVES)
                                    && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CAMPFIRE)
                                    && !state.is(Blocks.SOUL_CAMPFIRE)) {
                                return checkPos.above();
                            }
                            if (water == null && state.is(Blocks.WATER)) water = checkPos.above();
                        }
                        break;
                    }
                }
            }
        }

        return water;
    }

    private static boolean isSolidBlock(BlockState state, Level level, BlockPos pos) {
        return state.isSolid() &&
                state.isCollisionShapeFullBlock(level, pos) &&
                !state.isAir();
    }

    private static boolean hasEnoughSpace(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir() &&
                level.getBlockState(pos.above()).isAir();
    }
}
