package mx.issacinzunza.biosphereworlds.mixin;

import com.mojang.datafixers.DataFixer;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereGeneratorAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldData;
import mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carga la receta antes de construir ruido, estructuras y workers del ChunkMap. */
@Mixin(ServerChunkCache.class)
public abstract class MixinClassicWorldStorage {
    @Shadow @Final private DimensionDataStorage dataStorage;

    @Inject(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/server/level/ChunkMap"))
    private void bindClassicWorld(ServerLevel level, LevelStorageSource.LevelStorageAccess storage,
            DataFixer fixer, StructureTemplateManager structures, Executor dispatcher, ChunkGenerator generator,
            int viewDistance, int simulationDistance, boolean sync, ChunkProgressListener progress,
            ChunkStatusUpdateListener status, Supplier<DimensionDataStorage> overworldStorage, CallbackInfo ci) {
        if (!BiosphereWorldgen.isBiosphereGenerator(generator) || level.dimension() != Level.OVERWORLD) return;
        var file = storage.getDimensionPath(level.dimension()).resolve("data").resolve(ClassicWorldData.ID + ".dat");
        boolean newWorld = !level.getServer().getWorldData().overworldData().isInitialized();
        var world = ClassicWorldData.load(dataStorage, file, newWorld, ClassicWorldSettings::capture);
        if (world != null) ((BiosphereGeneratorAccess) generator).biosphereworlds$setClassicWorld(world);
    }
}
