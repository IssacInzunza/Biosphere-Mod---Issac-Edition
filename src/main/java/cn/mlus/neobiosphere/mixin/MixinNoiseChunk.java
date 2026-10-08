package cn.mlus.neobiosphere.mixin;

import cn.mlus.neobiosphere.worldgen.BiosphereNoiseChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.world.level.levelgen.NoiseChunk.class)
public abstract class MixinNoiseChunk implements BiosphereNoiseChunkAccess {
    @Unique
    private boolean neobiosphere$biosphere;

    @Override
    public boolean neobiosphere$isBiosphere() {
        return neobiosphere$biosphere;
    }

    @Override
    public void neobiosphere$setBiosphere(boolean biosphere) {
        neobiosphere$biosphere = biosphere;
    }
}