package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarStronghold;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(StrongholdStructure.class)
public abstract class MixinSolarStronghold {
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void solarCentralOnly(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!(context.chunkGenerator() instanceof SolarChunkGenerator solar)) return;
        var center = solar.layout().systemAtBlock(context.chunkPos().getMiddleBlockX(), context.chunkPos().getMiddleBlockZ()).central();
        if (context.chunkPos().x != Math.floorDiv((int) center.x(), 16) || context.chunkPos().z != Math.floorDiv((int) center.z(), 16)) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "generatePieces", at = @At("HEAD"), cancellable = true)
    private static void solarPieces(StructurePiecesBuilder builder, Structure.GenerationContext context, CallbackInfo ci) {
        if (!(context.chunkGenerator() instanceof SolarChunkGenerator solar)) return;
        SolarStronghold.generate(builder, context, solar);
        ci.cancel();
    }
}
