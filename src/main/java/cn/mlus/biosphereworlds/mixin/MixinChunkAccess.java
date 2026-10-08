package cn.mlus.biosphereworlds.mixin;

import cn.mlus.biosphereworlds.worldgen.BiosphereChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.world.level.chunk.ChunkAccess.class)
public abstract class MixinChunkAccess implements BiosphereChunkAccess {
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
