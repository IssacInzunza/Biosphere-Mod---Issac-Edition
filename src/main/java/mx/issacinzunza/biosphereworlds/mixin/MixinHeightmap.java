package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.util.SphereUtil;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Heightmap.class)
public class MixinHeightmap {

    @Shadow
    @Final
    private ChunkAccess chunk;

    @ModifyVariable(
            method = "update",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private BlockState onUpdateBlockState(BlockState state, int x, int y, int z) {
        if (((SolarChunkAccess) this.chunk).biosphereworlds$solarLayout() != null) {
            return SolarChunkGenerator.heightmapState(this.chunk, solarWorldPosition(this.chunk, new BlockPos(x, y, z)), state);
        }
        if (!BiosphereWorldgen.isBiosphereChunk(this.chunk)) {
            return state;
        }
        if (SphereUtil.isSphereBlock(state) && y > SphereConfig.CENTER_Y.get().intValue()) {
            return Blocks.AIR.defaultBlockState();
        }
        // Todo el volumen de un satelite se comporta como aire: cascaron, cuerpo de
        // piedra y cualquier bloque que ocupe su interior. Si no, un satelite sobre
        // una esfera contaminaria WORLD_SURFACE_WG / OCEAN_FLOOR_WG y cambiaria las
        // calles de aldea y la vegetacion de abajo.
        if (SphereConfig.SATELLITES_ENABLED.get() && SphereLayout.current().isInsideSatellite(x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState solarDownwardRead(ChunkAccess instance, BlockPos pos) {
        BlockState original = instance.getBlockState(pos);
        if (((SolarChunkAccess) instance).biosphereworlds$solarLayout() == null) {
            return original;
        }
        return SolarChunkGenerator.heightmapState(instance, solarWorldPosition(instance, pos), original);
    }

    @Redirect(
            method = "primeHeightmaps",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private static BlockState onPrimeGetBlockState(ChunkAccess instance, BlockPos pos) {
        BlockState original = instance.getBlockState(pos);
        if (((SolarChunkAccess) instance).biosphereworlds$solarLayout() != null) {
            return SolarChunkGenerator.heightmapState(instance, solarWorldPosition(instance, pos), original);
        }
        if (!BiosphereWorldgen.isBiosphereChunk(instance)) {
            return original;
        }
        if (SphereUtil.isSphereBlock(original) && pos.getY() > SphereConfig.CENTER_Y.get().intValue()) {
            return Blocks.AIR.defaultBlockState();
        }
        if (SphereConfig.SATELLITES_ENABLED.get()
                && SphereLayout.current().isInsideSatellite(pos.getX(), pos.getY(), pos.getZ())) {
            return Blocks.AIR.defaultBlockState();
        }
        return original;
    }

    private static BlockPos solarWorldPosition(ChunkAccess chunk, BlockPos local) {
        return new BlockPos(chunk.getPos().getMinBlockX() + (local.getX() & 15), local.getY(),
                chunk.getPos().getMinBlockZ() + (local.getZ() & 15));
    }
}
