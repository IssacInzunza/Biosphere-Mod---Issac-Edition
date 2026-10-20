package mx.issacinzunza.biosphereworlds.worldgen.solar;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

/**
 * Authorizes natural End portal writes only while placing the central Stronghold's portal room.
 * Scope is local to each generation thread and restored even when piece placement throws.
 * Both worldgen write paths delegate here after checking that the world/chunk is Solar.
 */
public final class SolarPortalGuard {
    /** Clearance from the sphere shell, in blocks, required by both portal write paths. */
    private static final int PORTAL_MARGIN = 3;
    private static final ThreadLocal<BoundingBox> PORTAL_ROOM = new ThreadLocal<>();
    private SolarPortalGuard() { }

    /**
     * Evaluates the complete natural-portal policy without reading global configuration.
     * @param geometry layout of an already identified Solar world/chunk
     * @param pos global block position being written
     * @return true only inside both the active portal room and the central sphere's safe interior
     */
    public static boolean allowsNaturalPortal(SolarLayout geometry, BlockPos pos) {
        var body = geometry.bodyAt(pos.getX(), pos.getY(), pos.getZ(), PORTAL_MARGIN);
        BoundingBox room = PORTAL_ROOM.get();
        return room != null && room.isInside(pos) && body != null && body.central();
    }

    /**
     * Runs the original piece placement inside a temporary authorization scope.
     * Non-Solar generators delegate immediately, without accessing thread-local state.
     * @param piece vanilla structure piece being placed
     * @param level generation region supplied by the engine
     * @param structures structure access for this placement
     * @param generator generator identifying the preset
     * @param random engine-owned random source, passed through unchanged
     * @param box chunk-clipped placement bounds
     * @param chunk chunk receiving this placement
     * @param pivot vanilla placement pivot
     * @param centralStronghold whether the caller verified this is the central Stronghold start
     */
    public static void place(StructurePiece piece, WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                             RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot, boolean centralStronghold) {
        if (!(generator instanceof SolarChunkGenerator)) {
            piece.postProcess(level, structures, generator, random, box, chunk, pivot);
            return;
        }
        BoundingBox previous = PORTAL_ROOM.get();
        if (centralStronghold && piece instanceof StrongholdPieces.PortalRoom) PORTAL_ROOM.set(piece.getBoundingBox());
        else PORTAL_ROOM.remove();
        try {
            piece.postProcess(level, structures, generator, random, box, chunk, pivot);
        } finally {
            if (previous != null) PORTAL_ROOM.set(previous);
            else PORTAL_ROOM.remove();
        }
    }
}
