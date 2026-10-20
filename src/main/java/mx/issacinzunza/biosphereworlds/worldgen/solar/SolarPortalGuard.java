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

public final class SolarPortalGuard {
    private static final ThreadLocal<BoundingBox> PORTAL_ROOM = new ThreadLocal<>();
    private SolarPortalGuard() { }
    public static boolean allows(BlockPos pos) {
        BoundingBox box = PORTAL_ROOM.get();
        return box != null && box.isInside(pos);
    }
    public static void place(StructurePiece piece, WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                             RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot, boolean centralStronghold) {
        if (!(generator instanceof SolarChunkGenerator)) {
            piece.postProcess(level, structures, generator, random, box, chunk, pivot);
            return;
        }
        BoundingBox previous = PORTAL_ROOM.get();
        if (centralStronghold && piece instanceof StrongholdPieces.PortalRoom) PORTAL_ROOM.set(piece.getBoundingBox());
        else PORTAL_ROOM.remove();
        try { piece.postProcess(level, structures, generator, random, box, chunk, pivot); }
        finally {
            if (previous != null) PORTAL_ROOM.set(previous);
            else PORTAL_ROOM.remove();
        }
    }
}
