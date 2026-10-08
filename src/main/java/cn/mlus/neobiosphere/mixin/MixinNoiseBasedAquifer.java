package cn.mlus.neobiosphere.mixin;

import cn.mlus.neobiosphere.carver.SphereCarver;
import cn.mlus.neobiosphere.config.SphereConfig;
import cn.mlus.neobiosphere.worldgen.BiosphereWorldgen;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public class MixinNoiseBasedAquifer {
    @Shadow @Final private net.minecraft.world.level.levelgen.NoiseChunk noiseChunk;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void markBiosphereAquifer(CallbackInfo ci) {
        if (BiosphereWorldgen.isBiosphereNoiseChunk(noiseChunk)) {
            BiosphereWorldgen.markAquifer((Aquifer.NoiseBasedAquifer)(Object)this);
        }
    }

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void onComputeSubstance(DensityFunction.FunctionContext context, double substance, CallbackInfoReturnable<BlockState> cir) {
        if (!BiosphereWorldgen.isBiosphereAquifer((Aquifer.NoiseBasedAquifer)(Object)this)) {
            return;
        }

        int sphereSpacing = SphereConfig.SPACING.get().intValue();
        int centerY = SphereConfig.CENTER_Y.get().intValue();

        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();

        int sphereRadius = SphereCarver.getSphereRadiusAt(x, z);

        double gridX = Math.round((float) x / sphereSpacing) * sphereSpacing;
        double gridZ = Math.round((float) z / sphereSpacing) * sphereSpacing;

        double dx = x - gridX;
        double dy = y - centerY;
        double dz = z - gridZ;

        double distance = dx * dx + dy * dy + dz * dz;
        if (distance - sphereRadius * sphereRadius >= -sphereRadius * 2) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
