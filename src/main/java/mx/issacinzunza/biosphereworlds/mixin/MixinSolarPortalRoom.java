package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPortalGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructureStart.class)
public abstract class MixinSolarPortalRoom {
    @Redirect(method = "placeInChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"))
    private void solarPortalScope(StructurePiece piece, WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                  RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        var start = (StructureStart) (Object) this;
        boolean central = false;
        if (generator instanceof mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator solar) {
            var body = solar.layout().systemAtBlock(start.getChunkPos().getMiddleBlockX(), start.getChunkPos().getMiddleBlockZ()).central();
            central = solar.isCentralStronghold(start.getStructure())
                    && start.getChunkPos().x == Math.floorDiv((int) body.x(), 16)
                    && start.getChunkPos().z == Math.floorDiv((int) body.z(), 16);
        }
        SolarPortalGuard.place(piece, level, structures, generator, random, box, chunk, pivot, central,
                !(start.getStructure() instanceof net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure));
    }
}
