package mx.issacinzunza.biosphereworlds.gametest;

import java.util.EnumSet;
import mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers;
import mx.issacinzunza.biosphereworlds.worldgen.solar.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Literal volume-writing baseline from before the modular refactor; engine regression only. */
final class SolarVolumeBaseline {
    private final SolarLayout layout;
    private final SolarSettings solarSettings;
    private final java.util.List<SatelliteLayers.Mineral> minerals;
    private final int seaLevel;
    SolarVolumeBaseline(SolarLayout layout, SolarSettings settings, java.util.List<SatelliteLayers.Mineral> minerals, int seaLevel) {
        this.layout=layout; this.solarSettings=settings; this.minerals=minerals; this.seaLevel=seaLevel;
    }
    private SolarLayout layout() { return layout; }
    private int getSeaLevel() { return seaLevel; }
    void finishVolume(ChunkAccess chunk, boolean shell, boolean centralSurface) {
        var system = layout().systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
        var bodies = system.bodies().stream().filter(b -> touches(chunk, b.x(), b.z(), b.radius())).toList();
        var moons = system.moons().stream().filter(m -> touches(chunk, m.sphere().centerX(), m.sphere().centerZ(), m.sphere().radius())).toList();
        if (bodies.isEmpty() && moons.isEmpty()) return;
        boolean extreme = centralSurface && solarSettings.layoutVersion() >= 2
                && solarSettings.centralClimate() != SolarLayout.Climate.TEMPERATE && bodies.stream().anyMatch(SolarLayout.Body::central);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int surfaceY = Integer.MIN_VALUE;
                if (extreme) {
                    for (int y = chunk.getMaxBuildHeight() - 1; y >= 56; y--) {
                        var state = chunk.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && state.getFluidState().isEmpty()) { surfaceY = y; break; }
                    }
                }
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                    SolarLayout.Body containing = null;
                    for (var body : bodies) {
                        if (body.contains(x, y, z, 0)) { containing = body; break; }
                    }
                    pos.set(x, y, z);
                    BlockState replacement = null;
                    if (containing == null) replacement = Blocks.AIR.defaultBlockState();
                    else if (shell && layout().shellBoundary(containing, x, y, z)) replacement = shellState(containing);
                    else if (shell && SolarTerrain.bedrockLiner(containing, x, y, z, solarSettings.environment().bedrockLiner())) {
                        replacement = Blocks.BEDROCK.defaultBlockState();
                    } else if (!shell) {
                        var environment = solarSettings.environment();
                        int ocean = solarSettings.layoutVersion() >= 5 ? 0 : SolarTerrain.legacyOceanOverride(containing, x, y, z, environment.edgeOceanEnabled(),
                                environment.edgeOceanWidth(), environment.edgeOceanDepth(), getSeaLevel());
                        if (ocean == 1) replacement = Blocks.AIR.defaultBlockState();
                        else if (ocean == 2) replacement = Blocks.WATER.defaultBlockState();
                        var existing = replacement == null ? chunk.getBlockState(pos) : replacement;
                        if (solarSettings.layoutVersion() >= 2 && solarSettings.general().bedrockMode()
                                == mx.issacinzunza.biosphereworlds.config.SphereConfig.BedrockMode.NONE && existing.is(Blocks.BEDROCK)) {
                            replacement = Blocks.DEEPSLATE.defaultBlockState();
                        }
                        if (extreme && containing.central() && containing.contains(x, y, z, 2)) {
                            boolean cold = solarSettings.centralClimate() == SolarLayout.Climate.COLD;
                            if (SolarTerrain.centralFluid(y, existing.is(Blocks.WATER))) {
                                replacement = cold ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.LAVA.defaultBlockState();
                            } else if (SolarTerrain.centralSurface(y, surfaceY) && !existing.isAir()
                                    && existing.getFluidState().isEmpty() && !existing.is(Blocks.BEDROCK)) {
                                replacement = cold ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
                            }
                        }
                    }
                    if (replacement != null && !chunk.getBlockState(pos).equals(replacement)) chunk.setBlockState(pos, replacement, false);
                }
            }
        }
        if (shell) {
            for (var moon : moons) {
                var s = moon.sphere();
                var layers = SatelliteLayers.layers(s.radius());
                var mineral = layers.hasCore() ? SatelliteLayers.selectMineral(s.seed(), minerals) : null;
                BlockState satelliteShell = shellState(system.bodies().stream().filter(b -> b.id() == moon.planetId()).findFirst().orElseThrow());
                BlockState ore = mineral == null ? Blocks.STONE.defaultBlockState()
                        : BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mineral.blockId())).defaultBlockState();
                for (int x = Math.max(chunk.getPos().getMinBlockX(), (int) Math.floor(s.centerX() - s.radius()));
                     x <= Math.min(chunk.getPos().getMaxBlockX(), (int) Math.ceil(s.centerX() + s.radius())); x++) {
                    for (int z = Math.max(chunk.getPos().getMinBlockZ(), (int) Math.floor(s.centerZ() - s.radius()));
                         z <= Math.min(chunk.getPos().getMaxBlockZ(), (int) Math.ceil(s.centerZ() + s.radius())); z++) {
                        for (int y = (int) Math.floor(s.centerY() - s.radius()); y <= Math.ceil(s.centerY() + s.radius()); y++) {
                            double d = Math.pow(x - s.centerX(), 2) + Math.pow(y - s.centerY(), 2) + Math.pow(z - s.centerZ(), 2);
                            if (d > (double) s.radius() * s.radius()) continue;
                            BlockState state = switch (SatelliteLayers.regionAt(layers, d)) {
                                case SHELL -> satelliteShell;
                                case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
                                case STONE -> Blocks.STONE.defaultBlockState();
                                case CORE -> mineral != null && SatelliteLayers.isOre(s.seed(), x, y, z, mineral.densityPermille())
                                        ? ore : Blocks.STONE.defaultBlockState();
                            };
                            chunk.setBlockState(pos.set(x, y, z), state, false);
                        }
                    }
                }
            }
        }
        // primeHeightmaps doesn't reset columns that have become completely empty.
        for (var type : Heightmap.Types.values()) java.util.Arrays.fill(chunk.getOrCreateHeightmapUnprimed(type).getRawData(), 0);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
    }
    private static boolean touches(ChunkAccess chunk, double x, double z, int radius) {
        double dx = Math.max(Math.max(chunk.getPos().getMinBlockX() - x, 0), x - chunk.getPos().getMaxBlockX());
        double dz = Math.max(Math.max(chunk.getPos().getMinBlockZ() - z, 0), z - chunk.getPos().getMaxBlockZ());
        return dx * dx + dz * dz <= (double) radius * radius;
    }
    public BlockState shellState(SolarLayout.Body body) {
        var blocks = solarSettings.general().shellBlocks();
        if (blocks.isEmpty()) return Blocks.GLASS.defaultBlockState();
        var id = ResourceLocation.tryParse(blocks.get((int) Math.floorMod(body.id(), blocks.size())));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Blocks.GLASS.defaultBlockState();
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        return state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME) ? Blocks.GLASS.defaultBlockState() : state;
    }
}
