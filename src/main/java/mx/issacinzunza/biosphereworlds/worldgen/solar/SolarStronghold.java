package mx.issacinzunza.biosphereworlds.worldgen.solar;

import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

/** Vanilla pieces, complete graph and portal room, with bounded retries and full containment. */
public final class SolarStronghold {
    private SolarStronghold() { }

    /**
     * Builds the full vanilla graph and fits it below the central surface reservation.
     * Vanilla piece weights are protected by their class monitor; randomness and retry
     * ordering stay unchanged so existing seeds retain their generated pieces.
     * @param builder engine-owned piece builder, cleared between attempts
     * @param context seeded vanilla structure context
     * @param generator bound Solar generator supplying central geometry
     * @throws IllegalStateException if the start is outside the center or no complete graph fits
     */
    public static void generate(StructurePiecesBuilder builder, Structure.GenerationContext context, SolarChunkGenerator generator) {
        var body = generator.layout().systemAtBlock(context.chunkPos().getMiddleBlockX(), context.chunkPos().getMiddleBlockZ()).central();
        if (context.chunkPos().x != Math.floorDiv((int) body.x(), 16)
                || context.chunkPos().z != Math.floorDiv((int) body.z(), 16)) {
            throw new IllegalStateException("Attempt to generate a Solar stronghold outside its central start chunk");
        }
        // StrongholdPieces has static mutable weights in this exact Minecraft version.
        synchronized (StrongholdPieces.class) {
            for (int attempt = 0; attempt < 128; attempt++) {
                builder.clear();
                context.random().setLargeFeatureSeed(context.seed() + attempt, context.chunkPos().x, context.chunkPos().z);
                StrongholdPieces.resetPieces();
                var start = new StrongholdPieces.StartPiece(context.random(), context.chunkPos().getBlockX(2), context.chunkPos().getBlockZ(2));
                builder.addPiece(start);
                start.addChildren(start, builder, context.random());
                while (!start.pendingChildren.isEmpty()) {
                    StructurePiece piece = start.pendingChildren.remove(context.random().nextInt(start.pendingChildren.size()));
                    piece.addChildren(start, builder, context.random());
                }
                if (start.portalRoomPiece == null) continue;
                var box = builder.getBoundingBox();
                int dx = (int) body.x() - box.getCenter().getX();
                int dz = (int) body.z() - box.getCenter().getZ();
                int dy = SolarTerrain.STRONGHOLD_MAX_Y - box.maxY();
                for (var piece : builder.build().pieces()) piece.move(dx, dy, dz);
                box = builder.getBoundingBox();
                if (box.minY() >= context.heightAccessor().getMinBuildHeight()
                        && box.maxY() < context.heightAccessor().getMaxBuildHeight()
                        && body.containsBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), SolarTerrain.STRONGHOLD_INTERIOR_MARGIN)) return;
            }
        }
        builder.clear();
        throw new IllegalStateException("Unable to fit a complete Solar stronghold after 128 attempts at " + context.chunkPos());
    }
}
