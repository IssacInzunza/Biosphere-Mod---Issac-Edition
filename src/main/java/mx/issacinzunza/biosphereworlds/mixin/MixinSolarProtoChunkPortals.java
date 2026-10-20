package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkAccess;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPortalGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also covers ordinary direct writes to a Solar ProtoChunk, bypassing WorldGenRegion. */
@Mixin(ProtoChunk.class)
public abstract class MixinSolarProtoChunkPortals {
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void solarPortalBlocks(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        if (!state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_PORTAL_FRAME)) return;
        var geometry = ((SolarChunkAccess) this).biosphereworlds$solarLayout();
        if (geometry == null) return;
        if (!SolarPortalGuard.allowsNaturalPortal(geometry, pos)) cir.setReturnValue(null);
    }
}
