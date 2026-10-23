package mx.issacinzunza.biosphereworlds.worldgen.solar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** Retains vanilla RandomSpread lookup, but uses the same centers as SolarLayout. */
public final class SolarPlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<SolarPlacement> CODEC = SolarSettings.GEOMETRY_CODEC.fieldOf("geometry")
            .xmap(SolarPlacement::new, p -> p.params);
    private final SolarLayout.Params params;

    public SolarPlacement(SolarLayout.Params params) {
        super(params.spacing() / 16, 0, RandomSpreadType.LINEAR, 17266323);
        this.params = params;
    }

    @Override public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        int cellX = Math.floorDiv(chunkX, spacing());
        int cellZ = Math.floorDiv(chunkZ, spacing());
        // No planet or satellite generation is needed for this query.
        var center = SolarLayout.centralAtCell(seed, params, cellX, cellZ);
        return new ChunkPos(Math.floorDiv((int) center.x(), 16), Math.floorDiv((int) center.z(), 16));
    }

    @Override public BlockPos getLocatePos(ChunkPos pos) {
        return new BlockPos(pos.getMiddleBlockX(), 32, pos.getMiddleBlockZ());
    }
    @Override public StructurePlacementType<?> type() { return SolarWorldgen.PLACEMENT.get(); }
}
