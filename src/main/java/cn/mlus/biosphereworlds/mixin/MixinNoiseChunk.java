package cn.mlus.biosphereworlds.mixin;

import cn.mlus.biosphereworlds.worldgen.BiosphereNoiseChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.world.level.levelgen.NoiseChunk.class)
public abstract class MixinNoiseChunk implements BiosphereNoiseChunkAccess {
    @Unique
    private boolean biosphereworlds$biosphere;

    @Override
    public boolean biosphereworlds$isBiosphere() {
        return biosphereworlds$biosphere;
    }

    @Override
    public void biosphereworlds$setBiosphere(boolean biosphere) {
        biosphereworlds$biosphere = biosphere;
    }
}