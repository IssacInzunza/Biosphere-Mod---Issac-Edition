package cn.mlus.neobiosphere.mixin;

import cn.mlus.neobiosphere.worldgen.BiosphereGeneratorAccess;
import cn.mlus.neobiosphere.worldgen.BiosphereWorldgen;
import cn.mlus.neobiosphere.config.SphereConfig;
import cn.mlus.neobiosphere.Neobiosphere;
import net.minecraft.core.QuartPos;
import cn.mlus.neobiosphere.carver.SphereCarver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGeneratorBiomeSource {
    @Inject(method = "getBiomeSource", at = @At("RETURN"))
    private void markBiosphereBiomeSource(CallbackInfoReturnable<BiomeSource> cir) {
        if ((Object) this instanceof BiosphereGeneratorAccess access && access.neobiosphere$isBiosphereGenerator()) {
            BiosphereWorldgen.markBiomeSource(cir.getReturnValue());
            if (SphereConfig.DEBUG_BIOME_LOOKUPS.get()) {
                int x = QuartPos.toBlock(0);
                int z = QuartPos.toBlock(0);
                Neobiosphere.LOGGER.debug("biome-route=ChunkGenerator#getBiomeSource source={} marked={} outside-origin={}",
                        cir.getReturnValue().getClass().getName(),
                        BiosphereWorldgen.isBiosphereBiomeSource(cir.getReturnValue()),
                        !SphereCarver.isInsideAnySphere(x, SphereConfig.CENTER_Y.get().intValue(), z, -4));
            }
        }
    }
}
