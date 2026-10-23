package mx.issacinzunza.biosphereworlds.mixin;

import com.mojang.datafixers.DataFixer;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import mx.issacinzunza.biosphereworlds.density.ClassicDensityFunction;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adapta una copia del router antes de inicializar RandomState; no muta el registro compartido. */
@Mixin(ChunkMap.class)
public abstract class MixinClassicChunkMap {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState classicRandomState(NoiseGeneratorSettings template, HolderGetter<NormalNoise.NoiseParameters> noises, long seed,
            ServerLevel level, LevelStorageSource.LevelStorageAccess storage, DataFixer fixer,
            StructureTemplateManager structures, Executor dispatcher, BlockableEventLoop<Runnable> loop,
            LightChunkGetter light, ChunkGenerator generator, ChunkProgressListener progress,
            ChunkStatusUpdateListener status, Supplier<DimensionDataStorage> overworldStorage, int viewDistance, boolean sync) {
        var world = BiosphereWorldgen.isBiosphereGenerator(generator) ? BiosphereWorldgen.classicWorld(generator) : null;
        if (world != null) {
            template = new NoiseGeneratorSettings(template.noiseSettings(), template.defaultBlock(), template.defaultFluid(),
                    ClassicDensityFunction.adapt(template.noiseRouter(), world), template.surfaceRule(), template.spawnTarget(),
                    template.seaLevel(), template.disableMobGeneration(), template.isAquifersEnabled(), template.oreVeinsEnabled(),
                    template.useLegacyRandomSource());
        }
        return RandomState.create(template, noises, seed);
    }
}
