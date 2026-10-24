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
import org.spongepowered.asm.mixin.Unique;
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
        return biosphereworlds$heightmapState(this.chunk, x, y, z, state, true);
    }

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState onDownwardRead(ChunkAccess instance, BlockPos pos) {
        return biosphereworlds$heightmapState(instance, pos.getX(), pos.getY(), pos.getZ(), instance.getBlockState(pos), false);
    }

    @Redirect(
            method = "primeHeightmaps",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private static BlockState onPrimeGetBlockState(ChunkAccess instance, BlockPos pos) {
        return biosphereworlds$heightmapState(instance, pos.getX(), pos.getY(), pos.getZ(), instance.getBlockState(pos), true);
    }

    /** Aplica la misma politica en primado, escritura y busqueda descendente. */
    @Unique
    private static BlockState biosphereworlds$heightmapState(ChunkAccess chunk, int x, int y, int z, BlockState original,
                                                           boolean filterClassicShell) {
        if (((SolarChunkAccess) chunk).biosphereworlds$solarLayout() != null) {
            return SolarChunkGenerator.heightmapState(chunk, new BlockPos(
                    chunk.getPos().getMinBlockX() + (x & 15), y, chunk.getPos().getMinBlockZ() + (z & 15)), original);
        }
        if (!BiosphereWorldgen.isBiosphereChunk(chunk)) {
            return original;
        }
        // La correccion de satelites no cambia el filtrado historico del cascaron principal.
        var world = BiosphereWorldgen.classicWorld(chunk);
        boolean upper = world == null ? SphereConfig.UPPER_SHELL_ENABLED.get() : world.settings().upperShellEnabled();
        boolean lower = world == null ? SphereConfig.LOWER_SHELL_ENABLED.get() : world.settings().lowerShellEnabled();
        if (!upper || !lower) {
            var layout = BiosphereWorldgen.classicLayout(chunk);
            int blockX = chunk.getPos().getMinBlockX() + (x & 15);
            int blockZ = chunk.getPos().getMinBlockZ() + (z & 15);
            boolean cap = world == null ? SphereConfig.CLOSE_OPEN_TOP.get() : world.settings().closeOpenTop();
            var sphere = layout.mainShellBandAt(blockX, y, blockZ, cap, chunk.getMaxBuildHeight() - 1, false);
            if (sphere != null && (original.is(Blocks.GLASS)
                    || (SphereLayout.shellHemisphereEnabled(y, sphere.centerY(), upper, lower)
                    && (world == null ? SphereUtil.isSphereBlock(original) : world.isShell(original))))) {
                return Blocks.AIR.defaultBlockState();
            }
            if (original.is(Blocks.BEDROCK)
                    && (world == null ? SphereConfig.BEDROCK_MODE.get() : world.settings().bedrockMode()) == SphereConfig.BedrockMode.SHELL
                    && layout.mainShellBandAt(blockX, y, blockZ, false, 0, true) != null) {
                return Blocks.AIR.defaultBlockState();
            }
        } else if (filterClassicShell && (world == null ? SphereUtil.isSphereBlock(original) : world.isShell(original))
                && y > (world == null ? SphereConfig.CENTER_Y.get().intValue() : world.settings().geometry().centerY())) {
            return Blocks.AIR.defaultBlockState();
        }
        if ((world == null ? SphereConfig.SATELLITES_ENABLED.get() : world.settings().geometry().satellitesEnabled())
                && BiosphereWorldgen.classicLayout(chunk).isInsideSatellite(chunk.getPos().getMinBlockX() + (x & 15), y,
                        chunk.getPos().getMinBlockZ() + (z & 15))) {
            return Blocks.AIR.defaultBlockState();
        }
        return original;
    }

}
