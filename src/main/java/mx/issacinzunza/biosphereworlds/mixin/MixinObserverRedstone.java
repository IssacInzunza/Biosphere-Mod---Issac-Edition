package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.easteregg.ObserverEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only active, explicitly collected sources in a Biospheres encounter may override signal queries. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinObserverRedstone {
    @Inject(method = {"getSignal", "getDirectSignal"}, at = @At("HEAD"), cancellable = true)
    private void observerSignal(BlockGetter getter, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (!(getter instanceof Level level)) return;
        int override = ObserverEvents.signalOverride(level, pos);
        if (override >= 0 && ((BlockBehaviour.BlockStateBase) (Object) this).isSignalSource()) cir.setReturnValue(override);
    }
}
