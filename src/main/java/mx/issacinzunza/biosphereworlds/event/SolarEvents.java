package mx.issacinzunza.biosphereworlds.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Comparator;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarGuardians;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkAccess;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;



/** Thin NeoForge event routes; geometry and guardian rules live in their owning modules. */
@EventBusSubscriber
public final class SolarEvents {
    private SolarEvents() { }

    @SubscribeEvent public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("biospheres")
                .then(Commands.literal("locate")
                        .then(Commands.literal("planets")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> locatePlanets(context.getSource()))
                                .then(Commands.literal("teleport")
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .then(Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                                        .then(Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                                                .executes(context -> teleportToPlanet(context.getSource(),
                                                                        DimensionArgument.getDimension(context, "dimension"),
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "z"))))))))));
    }

    private static int locatePlanets(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.player_only"));
            return 0;
        }
        if (!(player.serverLevel().getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.solar_only"));
            return 0;
        }
        var position = player.position();
        var system = generator.layout().systemContaining(position.x, position.z);
        if (system == null) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.outside_system"));
            return 0;
        }
        var planets = system.bodies().stream().filter(body -> !body.central())
                .sorted(Comparator.comparingDouble(body -> position.distanceToSqr(body.x(), body.y(), body.z())))
                .toList();
        source.sendSuccess(() -> Component.translatable("commands.biosphereworlds.planets.header", planets.size()), false);
        for (var planet : planets) {
            var climate = Component.translatable(switch (planet.climate()) {
                case HOT -> "commands.biosphereworlds.planets.climate.hot";
                case TEMPERATE -> "commands.biosphereworlds.planets.climate.temperate";
                case COLD -> "commands.biosphereworlds.planets.climate.cold";
            });
            long distance = Math.round(Math.sqrt(position.distanceToSqr(planet.x(), planet.y(), planet.z())));
            int x = (int) Math.floor(planet.x());
            int y = (int) Math.floor(planet.y());
            int z = (int) Math.floor(planet.z());
            String command = "/biospheres locate planets teleport " + player.serverLevel().dimension().location()
                    + " " + x + " " + z;
            var coordinates = Component.translatable("commands.biosphereworlds.planets.coordinates", x, y, z)
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("commands.biosphereworlds.planets.teleport_hint"))));
            source.sendSuccess(() -> Component.translatable("commands.biosphereworlds.planets.entry",
                    system.bodies().indexOf(planet), climate, coordinates, distance), false);
        }
        return planets.size();
    }

    private static int teleportToPlanet(CommandSourceStack source, ServerLevel destination, int x, int z) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.player_only"));
            return 0;
        }
        var level = player.serverLevel();
        if (!(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.solar_only"));
            return 0;
        }
        if (level != destination) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.wrong_dimension"));
            return 0;
        }
        var planet = generator.layout().systemAtBlock(x, z).bodies().stream()
                .filter(body -> !body.central() && (int) Math.floor(body.x()) == x && (int) Math.floor(body.z()) == z)
                .findFirst().orElse(null);
        if (planet == null) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.invalid_planet"));
            return 0;
        }
        var arrival = findPlanetArrival(level, planet, player);
        if (arrival == null) {
            source.sendFailure(Component.translatable("commands.biosphereworlds.planets.no_landing"));
            return 0;
        }
        player.stopRiding();
        player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        source.sendSuccess(() -> Component.translatable("commands.biosphereworlds.planets.teleported",
                arrival.getX(), arrival.getY(), arrival.getZ()), false);
        return 1;
    }

    /** Checks only each column's exterior surface; an unsafe surface never falls back to a cave. */
    private static BlockPos findPlanetArrival(ServerLevel level, SolarLayout.Body planet, ServerPlayer player) {
        int centerX = (int) Math.floor(planet.x());
        int centerZ = (int) Math.floor(planet.z());
        for (int ring = 0; ring <= 4; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    var border = level.getWorldBorder();
                    if (!border.isWithinBounds(new BlockPos(x, level.getMinBuildHeight(), z))) continue;
                    // Solar heightmaps already ignore the shell and moons. Include fluids so a lake
                    // blocks this column instead of exposing a dry cave below its bed.
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 1) continue;
                    var floor = new BlockPos(x, y, z);
                    var state = level.getBlockState(floor);
                    // Step through surface decoration only; stop immediately at an air gap.
                    while (floor.getY() > level.getMinBuildHeight() && !state.isAir()
                            && (state.is(Blocks.SNOW) || isClearForPlanetArrival(level, floor))) {
                        floor = floor.below();
                        state = level.getBlockState(floor);
                    }
                    y = floor.getY();
                    if (!planet.containsBox(x, y, z, x + 1, y + 3, z + 1, 2)
                            || !state.isCollisionShapeFullBlock(level, floor)
                            || !state.getFluidState().isEmpty() || isPlanetArrivalHazard(state)) continue;
                    var feet = floor.above();
                    // Leave a one-block descent onto snow rather than treating every snowy surface as blocked.
                    if (level.getBlockState(feet).is(Blocks.SNOW)) feet = feet.above();
                    if (!isClearForPlanetArrival(level, feet) || !isClearForPlanetArrival(level, feet.above())
                            || !planet.containsBox(x, feet.getY(), z, x + 1, feet.getY() + 2, z + 1, 2)) continue;
                    var bounds = player.getBoundingBox().move(x + 0.5 - player.getX(),
                            feet.getY() - player.getY(), z + 0.5 - player.getZ());
                    if (bounds.minX < border.getMinX() || bounds.maxX > border.getMaxX()
                            || bounds.minZ < border.getMinZ() || bounds.maxZ > border.getMaxZ()) continue;
                    if (level.noCollision(player, bounds) && !hasNearbyPlanetArrivalHazard(level, floor, feet)) return feet;
                }
            }
        }
        return null;
    }

    private static boolean isClearForPlanetArrival(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()
                && !state.is(Blocks.COBWEB) && !isPlanetArrivalHazard(state);
    }

    private static boolean hasNearbyPlanetArrivalHazard(ServerLevel level, BlockPos floor, BlockPos feet) {
        for (var pos : BlockPos.betweenClosed(floor.offset(-1, 0, -1), feet.offset(1, 1, 1))) {
            if (isPlanetArrivalHazard(level.getBlockState(pos))) return true;
        }
        return false;
    }

    private static boolean isPlanetArrivalHazard(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }

    @SubscribeEvent public static void guide(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(player.serverLevel().getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        if (!mx.issacinzunza.biosphereworlds.config.SolarConfig.OPTIONS.SOLAR_GUIDE_ENABLED.get()) return;
        if (player.tickCount % 10 != 0 || !player.connection.hasChannel(SolarGuidePayload.TYPE)) return;
        var center = generator.layout().systemAtBlock(player.getX(), player.getZ()).central();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SolarGuidePayload(player.level().dimension().location().toString(), center.x(), center.z(),
                        generator.layout().params().systemRadius()));
    }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        // No chunk access/loading here: restore only the per-chunk geometry reference.
        ((SolarChunkAccess) event.getChunk()).biosphereworlds$solarLayout(generator.layout());
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
        var spawn = new BlockPos(x, y, z);
        event.getSettings().setSpawn(spawn, 0);
        event.setCanceled(true);
    }

    /** Preserves the pre-cage spawning behavior for saved layouts 1–3. */
    @SubscribeEvent public static void legacyGuardians(LevelTickEvent.Post event) {
        SolarGuardians.tickLegacy(event);
    }

    /** Only marked cages in a Solar world may adjust a spawn position. */
    @SubscribeEvent public static void guardianPosition(MobSpawnEvent.PositionCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator)) return;
        SolarGuardians.adjustSpawn(event);
    }

    /** Tags guardians after position checks, without disabling vanilla mob initialization. */
    @SubscribeEvent public static void guardianFinalized(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof SolarChunkGenerator)) return;
        SolarGuardians.markSpawn(event);
    }

    /** Guardian explosions cannot remove the central sphere's shell. */
    @SubscribeEvent public static void protectShell(ExplosionEvent.Detonate event) {
        SolarGuardians.protectShell(event);
    }
}
