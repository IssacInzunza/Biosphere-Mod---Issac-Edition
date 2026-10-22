package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.mixin.accessor.StructureManagerAccessor;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGeneratorStructures {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void restrictStructureToSphere(StructureSelectionEntry structureSelectionEntry,
                                           StructureManager structureManager, RegistryAccess registryAccess,
                                           RandomState random, StructureTemplateManager structureTemplateManager,
                                           long seed, ChunkAccess chunk, ChunkPos chunkPos, SectionPos sectionPos,
                                           CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.world.level.LevelAccessor levelAccessor =
            ((StructureManagerAccessor) structureManager).biosphereworlds$getLevel();
        boolean isOverworld = levelAccessor instanceof Level level && level.dimension() == Level.OVERWORLD
            || levelAccessor instanceof WorldGenRegion region && region.getLevel().dimension() == Level.OVERWORLD;
        if (!BiosphereWorldgen.isBiosphereGenerator(this)
            || !SphereConfig.RESTRICT_STRUCTURES_TO_SPHERES.get()
            || !isOverworld) {
            return;
        }

        SphereLayout layout = SphereLayout.current();
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        SphereLayout.Sphere sphere = layout.sphereContaining(centerX, centerZ);
        int edgeMargin = SphereConfig.STRUCTURE_EDGE_MARGIN.get().intValue();

        // Solo restringe el inicio del chunk candidato; no garantiza que todas las piezas queden dentro.
        if (!layout.isInside(centerX, sphere.centerY(), centerZ, edgeMargin)) {
            cir.setReturnValue(false);
        }
    }
}
