package mx.issacinzunza.biosphereworlds.event;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarGuardians;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkAccess;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;



/** Thin NeoForge event routes; geometry and guardian rules live in their owning modules. */
@EventBusSubscriber
public final class SolarEvents {
    private SolarEvents() { }

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

    @SubscribeEvent public static void planetGuides(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(player.serverLevel().getChunkSource().getGenerator() instanceof SolarChunkGenerator generator)) return;
        if (!mx.issacinzunza.biosphereworlds.config.SolarConfig.OPTIONS.SOLAR_PLANET_GUIDES_ENABLED.get()) return;
        if (player.tickCount % 10 != 0 || !player.connection.hasChannel(SolarPlanetGuidePayload.TYPE)) return;
        var system = generator.layout().systemAtBlock(player.getX(), player.getZ());
        var center = system.central();
        var planets = mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPlanetGuide.markers(
                system, player.getX(), player.getY(), player.getZ());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SolarPlanetGuidePayload(new SolarGuidePayload(player.level().dimension().location().toString(),
                        center.x(), center.z(), generator.layout().params().systemRadius()), planets));
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
