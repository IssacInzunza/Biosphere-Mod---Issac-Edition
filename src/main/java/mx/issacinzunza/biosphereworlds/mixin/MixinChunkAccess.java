package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.BiosphereChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(net.minecraft.world.level.chunk.ChunkAccess.class)
public abstract class MixinChunkAccess implements BiosphereChunkAccess {
    @Unique
    private mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound biosphereworlds$classicWorld;

    @Override public mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound biosphereworlds$classicWorld() {
        if (((ChunkAccess) (Object) this).getLevel() instanceof ServerLevel level) {
            return BiosphereWorldgen.classicWorld(level.getChunkSource().getGenerator());
        }
        return biosphereworlds$classicWorld;
    }
    @Override public void biosphereworlds$setClassicWorld(mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound world) {
        biosphereworlds$classicWorld = world;
    }
    @Unique
    private boolean biosphereworlds$biosphere;

    @Override
    public boolean biosphereworlds$isBiosphere() {
        // LevelChunk conoce su mundo tanto tras promocion como tras leer de disco.
        // ProtoChunk sigue usando la marca establecida por el generador.
        if (((ChunkAccess) (Object) this).getLevel() instanceof ServerLevel level) {
            return BiosphereWorldgen.isBiosphereGenerator(level.getChunkSource().getGenerator());
        }
        return biosphereworlds$biosphere;
    }

    @Override
    public void biosphereworlds$setBiosphere(boolean biosphere) {
        biosphereworlds$biosphere = biosphere;
    }
}
