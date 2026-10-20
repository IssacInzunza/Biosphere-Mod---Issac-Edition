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
        event.getSettings().setSpawn(new BlockPos(x, y, z), 0);
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
