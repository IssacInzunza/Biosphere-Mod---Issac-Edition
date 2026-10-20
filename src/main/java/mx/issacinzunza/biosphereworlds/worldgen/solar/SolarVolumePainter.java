package mx.issacinzunza.biosphereworlds.worldgen.solar;

import java.util.EnumSet;
import java.util.List;
import mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Writes one world's shells, moons and central surface, independently of generator setup.
 * Immutable after construction and safe to share between chunk-generation workers.
 * Shell ids are resolved once, preserving palette order and per-sphere selection.
 */
public final class SolarVolumePainter {
    /** Lifecycle passes retain their engine ordering; they are not interchangeable. */
    public enum Pass { NOISE, SURFACE, SHELL }
    private final SolarLayout layout;
    private final SolarSettings solarSettings;
    private final List<SatelliteLayers.Mineral> minerals;
    private final List<BlockState> shellPalette;
    private final int seaLevel;

    /**
     * Prepares immutable writing resources. An empty mineral catalog is valid when no moons exist.
     * @param layout geometry belonging to this world
     * @param settings persisted Solar snapshot
     * @param minerals resolved immutable ore choices, or empty for disabled satellites
     * @param seaLevel water level of the shared noise template, in blocks
     */
    public SolarVolumePainter(SolarLayout layout, SolarSettings settings, List<SatelliteLayers.Mineral> minerals, int seaLevel) {
        this.layout = layout;
        this.solarSettings = settings;
        this.minerals = List.copyOf(minerals);
        this.seaLevel = seaLevel;
        var ids = settings.general().shellBlocks();
        shellPalette = ids.isEmpty() ? List.of(Blocks.GLASS.defaultBlockState()) : ids.stream().map(SolarVolumePainter::resolveShell).toList();
    }

    private static BlockState resolveShell(String raw) {
        var id = ResourceLocation.tryParse(raw);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Blocks.GLASS.defaultBlockState();
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        return state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME) ? Blocks.GLASS.defaultBlockState() : state;
    }

    /**
     * Selects a deterministic shell state without registry access during block writing.
     * @param body sphere whose stable id selects a palette entry
     * @return validated shell block state
     */
    public BlockState shellState(SolarLayout.Body body) {
        return shellPalette.get((int) Math.floorMod(body.id(), shellPalette.size()));
    }

    private SolarLayout layout() { return layout; }
    private int getSeaLevel() { return seaLevel; }

    /**
     * Applies only the requested worldgen pass, preserving legacy snapshots.
     * Upper air sections beyond both existing blocks and sphere tops are skipped;
     * the skipped region could only have received air in the literal implementation.
     * @param chunk chunk owned by the calling generation worker
     * @param pass noise trim, central surface treatment, or shell/moon construction
     */
    public void paint(ChunkAccess chunk, Pass pass) {
        boolean shell = pass == Pass.SHELL;
        boolean centralSurface = pass == Pass.SURFACE;
        var system = layout().systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
        var bodies = system.bodies().stream().filter(b -> touches(chunk, b.x(), b.z(), b.radius())).toList();
        var moons = system.moons().stream().filter(m -> touches(chunk, m.sphere().centerX(), m.sphere().centerZ(), m.sphere().radius())).toList();
        if (bodies.isEmpty() && moons.isEmpty()) return;
        boolean extreme = centralSurface && solarSettings.layoutVersion() >= 2
                && solarSettings.centralClimate() != SolarLayout.Climate.TEMPERATE && bodies.stream().anyMatch(SolarLayout.Body::central);
        int upperY = Math.min(chunk.getMaxBuildHeight() - 1, Math.max(
                chunk.getMinBuildHeight() + (chunk.getHighestFilledSectionIndex() + 1) * 16 - 1,
                bodies.stream().mapToInt(body -> (int) Math.ceil(body.y() + body.radius())).max().orElse(chunk.getMinBuildHeight() - 1)));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int surfaceY = Integer.MIN_VALUE;
                if (extreme) {
                    for (int y = upperY; y >= SolarTerrain.CENTRAL_HAZARD_MIN_Y; y--) {
                        var state = chunk.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && state.getFluidState().isEmpty()) { surfaceY = y; break; }
                    }
                }
                for (int y = chunk.getMinBuildHeight(); y <= upperY; y++) {
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
                            double d = moon.squaredDistanceTo(x, y, z);
                            if (!moon.containsSquaredDistance(d)) continue;
                            BlockState state = switch (SatelliteLayers.regionAt(layers, d)) {
                                case SHELL -> satelliteShell;
                                case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
                                case STONE -> Blocks.STONE.defaultBlockState();
                                case CORE -> mineral != null && SatelliteLayers.isOre(s.seed(), x, y, z, mineral.densityPermille())
                                        ? ore : Blocks.STONE.defaultBlockState();
                            };
                            pos.set(x, y, z);
                            if (!chunk.getBlockState(pos).equals(state)) chunk.setBlockState(pos, state, false);
                        }
                    }
                }
            }
        }
        // primeHeightmaps doesn't reset columns that have become completely empty.
        for (var type : Heightmap.Types.values()) java.util.Arrays.fill(chunk.getOrCreateHeightmapUnprimed(type).getRawData(), 0);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
    }

    /**
     * @param chunk chunk being considered for vanilla terrain/features
     * @return whether at least one planet intersects its horizontal footprint
     */
    public boolean touchesPlanet(ChunkAccess chunk) {
        return layout.systemAtBlock(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()).bodies().stream()
                .anyMatch(body -> touches(chunk, body.x(), body.z(), body.radius()));
    }

    private static boolean touches(ChunkAccess chunk, double x, double z, int radius) {
        double dx = Math.max(Math.max(chunk.getPos().getMinBlockX() - x, 0), x - chunk.getPos().getMaxBlockX());
        double dz = Math.max(Math.max(chunk.getPos().getMinBlockZ() - z, 0), z - chunk.getPos().getMaxBlockZ());
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /**
     * Filters Solar shell and moon blocks from heightmaps without global configuration.
     * @param chunk chunk whose Solar marker supplies geometry; unmarked chunks return unchanged
     * @param pos global block position, already normalized by the heightmap mixin
     * @param state original block state
     * @return air for ignored shell/moon blocks, otherwise the exact original state
     */
    public static BlockState heightmapState(ChunkAccess chunk, BlockPos pos, BlockState state) {
        SolarLayout geometry = ((SolarChunkAccess) chunk).biosphereworlds$solarLayout();
        if (geometry == null) return state;
        for (var moon : geometry.systemAtBlock(pos.getX(), pos.getZ()).moons()) {
            if (moon.contains(pos.getX(), pos.getY(), pos.getZ())) return Blocks.AIR.defaultBlockState();
        }
        if (geometry.version() == 1 && !state.is(Blocks.GLASS) && !state.is(Blocks.BEDROCK)) return state;
        var body = geometry.bodyAt(pos.getX(), pos.getY(), pos.getZ(), 0);
        if (body != null && state.is(Blocks.BEDROCK) && SolarTerrain.bedrockLiner(body, pos.getX(), pos.getY(), pos.getZ(), true)) {
            return Blocks.AIR.defaultBlockState();
        }
        return body != null && geometry.shellBoundary(body, pos.getX(), pos.getY(), pos.getZ()) ? Blocks.AIR.defaultBlockState() : state;
    }
}
