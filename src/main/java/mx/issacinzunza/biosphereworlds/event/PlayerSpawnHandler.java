package mx.issacinzunza.biosphereworlds.event;

import mx.issacinzunza.biosphereworlds.util.SphereUtil;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
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
        int radius;
        if (((ServerLevel) level).getChunkSource().getGenerator() instanceof SolarChunkGenerator solar) {
            var layout = solar.layout();
            var system = layout.systemAtBlock(currentPos.getX(), currentPos.getZ());
            var shells = system.bodies().stream().map(solar::shellState).map(BlockState::getBlock)
                    .collect(Collectors.toUnmodifiableSet());
            isShell = state -> shells.contains(state.getBlock());
            if (!isShell.test(level.getBlockState(currentPos))) return;
            var body = layout.bodyAt(currentPos.getX(), currentPos.getY(), currentPos.getZ(), 0);
            if (body != null) {
                radius = body.radius();
            } else {
                var moon = system.moons().stream().filter(m -> m.containsSquaredDistance(m.squaredDistanceTo(
                        currentPos.getX(), currentPos.getY(), currentPos.getZ()))).findFirst().orElse(null);
                if (moon == null) return;
                radius = moon.sphere().radius();
            }
        } else {
            isShell = SphereUtil::isSphereBlock;
            if (!isShell.test(level.getBlockState(currentPos))) return;
            radius = SphereLayout.current().sphereContaining(currentPos.getX(), currentPos.getZ()).radius();
        }

        BlockPos targetPos = findValidPosition(level, currentPos, radius, isShell);

        if (targetPos != null) {
            entity.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        }
    }

    private static BlockPos findValidPosition(Level level, BlockPos center, int radius, Predicate<BlockState> isShell) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        for (int y = center.getY(); y >= minY; y--) {
            BlockPos checkPos = new BlockPos(center.getX(), y, center.getZ());
            BlockState state = level.getBlockState(checkPos);

            if (isSolidBlock(state,level,checkPos) && !isShell.test(state)) {
                if (hasEnoughSpace(level, checkPos.above())) {
                    return checkPos.above();
                }
            }
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    for (int y = maxY; y >= minY; y--) {
                        BlockPos checkPos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        BlockState state = level.getBlockState(checkPos);

                        if (isSolidBlock(state,level,checkPos) && !isShell.test(state)) {
                            if (hasEnoughSpace(level, checkPos.above())) {
                                return checkPos.above();
                            }
                        }
                    }
                }
            }
        }

        return null;
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
