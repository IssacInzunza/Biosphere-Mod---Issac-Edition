package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.BiosphereNoiseChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.world.level.levelgen.NoiseChunk.class)
public abstract class MixinNoiseChunk implements BiosphereNoiseChunkAccess {
    @Unique
    private mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound biosphereworlds$classicWorld;

    @Override public mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound biosphereworlds$classicWorld() {
        return biosphereworlds$classicWorld;
    }
    @Override public void biosphereworlds$setClassicWorld(mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound world) {
        biosphereworlds$classicWorld = world;
    }
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
