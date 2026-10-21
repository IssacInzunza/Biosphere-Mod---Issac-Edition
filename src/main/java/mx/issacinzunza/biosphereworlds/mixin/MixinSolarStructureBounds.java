package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarStronghold;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class MixinSolarStructureBounds {
    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private void solarContainment(RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes, RandomState random,
                                  StructureTemplateManager templates, long seed, ChunkPos pos, int references,
                                  LevelHeightAccessor height, Predicate<Holder<Biome>> validBiome,
                                  CallbackInfoReturnable<StructureStart> cir) {
        if (!(generator instanceof SolarChunkGenerator solar) || !cir.getReturnValue().isValid()) return;
        boolean selectedStronghold = solar.isCentralStronghold((Structure) (Object) this);
        if (selectedStronghold && !((Object) this instanceof StrongholdStructure)) {
            var body = solar.layout().systemAtBlock(pos.getMiddleBlockX(), pos.getMiddleBlockZ()).central();
            var start = cir.getReturnValue();
            if (pos.x != Math.floorDiv((int) body.x(), 16) || pos.z != Math.floorDiv((int) body.z(), 16)
                    || !SolarStronghold.fitReplacement(start.getPieces(), body, height)) {
                cir.setReturnValue(StructureStart.INVALID_START);
            } else {
                // Replacement mods may have cached the pre-move bounding box.
                cir.setReturnValue(new StructureStart((Structure) (Object) this, pos, references,
                        new PiecesContainer(start.getPieces())));
            }
            return;
        }
        var box = net.minecraft.world.level.levelgen.structure.StructurePiece.createBoundingBox(cir.getReturnValue().getPieces().stream());
        var body = solar.layout().bodyAt(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ(), 0);
        boolean stronghold = (Object) this instanceof StrongholdStructure;
        if (body == null || (stronghold && !body.central()) || !body.containsBox(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(), solar.solarSettings().environment().bedrockLiner() ? 5 : 3)) {
            cir.setReturnValue(StructureStart.INVALID_START);
        }
    }
}
