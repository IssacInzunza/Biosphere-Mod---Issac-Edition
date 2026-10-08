package cn.mlus.neobiosphere.mixin;

import cn.mlus.neobiosphere.worldgen.BiosphereChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.world.level.chunk.ChunkAccess.class)
public abstract class MixinChunkAccess implements BiosphereChunkAccess {
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
