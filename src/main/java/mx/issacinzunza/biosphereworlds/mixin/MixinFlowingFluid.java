package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retiene el agua ya presente y la que intenta entrar al borde abierto, antes de su escritura. */
@Mixin(FlowingFluid.class)
public class MixinFlowingFluid {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void retainSource(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (BiosphereWorldgen.preserveWaterShell(level, pos, state)) ci.cancel();
    }

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void retainDestination(LevelAccessor level, BlockPos pos, BlockState blockState,
                                   Direction direction, FluidState state, CallbackInfo ci) {
        if (BiosphereWorldgen.preserveWaterShell(level, pos, state)) ci.cancel();
    }
}
