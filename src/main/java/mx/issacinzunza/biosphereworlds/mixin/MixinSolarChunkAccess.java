package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkAccess.class)
public abstract class MixinSolarChunkAccess implements SolarChunkAccess {
    @Unique private volatile SolarLayout biosphereworlds$solarLayout;
    @Override public SolarLayout biosphereworlds$solarLayout() { return biosphereworlds$solarLayout; }
    @Override public void biosphereworlds$solarLayout(SolarLayout layout) { biosphereworlds$solarLayout = layout; }
}
