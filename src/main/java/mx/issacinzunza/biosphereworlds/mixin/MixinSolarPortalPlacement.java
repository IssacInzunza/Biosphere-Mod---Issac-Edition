package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPortalGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class MixinSolarPortalPlacement {
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void solarPortalOnly(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        var region = (WorldGenRegion) (Object) this;
        if (!(region.getLevel().getChunkSource().getGenerator() instanceof SolarChunkGenerator solar)) return;
        var containing = solar.layout().bodyAt(pos.getX(), pos.getY(), pos.getZ(), 0);
        if (containing == null) {
            if (!state.isAir()) cir.setReturnValue(false);
            return;
        }
        if ((solar.layout().shellBoundary(containing, pos.getX(), pos.getY(), pos.getZ()) && !state.equals(solar.shellState(containing)))
                || (mx.issacinzunza.biosphereworlds.worldgen.solar.SolarTerrain.bedrockLiner(containing,
                pos.getX(), pos.getY(), pos.getZ(), solar.solarSettings().environment().bedrockLiner(),
                solar.layout().bedrockExposed(containing, pos.getY())) && !state.is(Blocks.BEDROCK))) {
            cir.setReturnValue(false);
            return;
        }
        if (state.isAir()) return;
        if (!state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_PORTAL_FRAME)) return;
        if (!SolarPortalGuard.allowsNaturalPortal(solar.layout(), pos)) cir.setReturnValue(false);
    }
}
