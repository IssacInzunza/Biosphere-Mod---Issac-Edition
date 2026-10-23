package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public class MixinNoiseBasedAquifer {
    @Shadow @Final private net.minecraft.world.level.levelgen.NoiseChunk noiseChunk;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void onComputeSubstance(DensityFunction.FunctionContext context, double substance, CallbackInfoReturnable<BlockState> cir) {
        // El NoiseChunk se marca al terminar su construccion, despues del acuifero.
        // Consultarlo aqui evita retener ambos objetos en un registro estatico.
        if (!BiosphereWorldgen.isBiosphereNoiseChunk(noiseChunk)) {
            return;
        }

        SphereLayout layout = BiosphereWorldgen.classicLayout(noiseChunk);

        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();

        SphereLayout.Sphere sphere = layout.sphereContaining(x, z);
        int sphereRadius = sphere.radius();

        double dx = x - sphere.centerX();
        double dy = y - sphere.centerY();
        double dz = z - sphere.centerZ();

        double distance = dx * dx + dy * dy + dz * dz;
        if (distance - sphereRadius * sphereRadius >= -sphereRadius * 2) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
