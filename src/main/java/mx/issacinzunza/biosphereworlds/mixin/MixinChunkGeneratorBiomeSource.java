package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.BiosphereGeneratorAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import net.minecraft.core.QuartPos;
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
                        !SphereLayout.current().isInside(x, SphereConfig.CENTER_Y.get().intValue(), z, -4));
            }
        }
    }
}
