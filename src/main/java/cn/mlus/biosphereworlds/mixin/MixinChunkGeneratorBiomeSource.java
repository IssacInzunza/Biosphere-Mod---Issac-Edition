package cn.mlus.biosphereworlds.mixin;

import cn.mlus.biosphereworlds.worldgen.BiosphereGeneratorAccess;
import cn.mlus.biosphereworlds.worldgen.BiosphereWorldgen;
import cn.mlus.biosphereworlds.config.SphereConfig;
import cn.mlus.biosphereworlds.BiosphereWorlds;
import net.minecraft.core.QuartPos;
import cn.mlus.biosphereworlds.carver.SphereCarver;
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
        if ((Object) this instanceof BiosphereGeneratorAccess access && access.biosphereworlds$isBiosphereGenerator()) {
            BiosphereWorldgen.markBiomeSource(cir.getReturnValue());
            if (SphereConfig.DEBUG_BIOME_LOOKUPS.get()) {
                int x = QuartPos.toBlock(0);
                int z = QuartPos.toBlock(0);
                BiosphereWorlds.LOGGER.debug("biome-route=ChunkGenerator#getBiomeSource source={} marked={} outside-origin={}",
                        cir.getReturnValue().getClass().getName(),
                        BiosphereWorldgen.isBiosphereBiomeSource(cir.getReturnValue()),
                        !SphereCarver.isInsideAnySphere(x, SphereConfig.CENTER_Y.get().intValue(), z, -4));
            }
        }
    }
}
