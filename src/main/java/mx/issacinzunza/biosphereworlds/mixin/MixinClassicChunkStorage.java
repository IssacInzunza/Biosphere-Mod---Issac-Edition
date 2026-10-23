package mx.issacinzunza.biosphereworlds.mixin;

import java.util.Set;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restaura tambien chunks incompletos antes de reconstruir sus mapas o continuar generando. */
@Mixin(ChunkSerializer.class)
public abstract class MixinClassicChunkStorage {
    @Unique
    private static void biosphereworlds$bindSavedRecipe(ServerLevel level, ChunkAccess chunk) {
        var generator = level.getChunkSource().getGenerator();
        if (!BiosphereWorldgen.isBiosphereGenerator(generator)) return;
        var world = BiosphereWorldgen.classicWorld(generator);
        if (world == null) return;
        var access = (BiosphereChunkAccess) chunk;
        access.biosphereworlds$setClassicWorld(world);
        access.biosphereworlds$setBiosphere(true);
    }

    @Redirect(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Heightmap;primeHeightmaps(Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/Set;)V"))
    private static void bindBeforeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types, ServerLevel level,
            PoiManager poi, RegionStorageInfo storage, ChunkPos pos, CompoundTag tag) {
        biosphereworlds$bindSavedRecipe(level, chunk);
        Heightmap.primeHeightmaps(chunk, types);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void bindReturnedChunk(ServerLevel level, PoiManager poi, RegionStorageInfo storage,
            ChunkPos pos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        biosphereworlds$bindSavedRecipe(level, cir.getReturnValue());
    }
}
