package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.event.SolarEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the marked Solar cages receive the per-system population limit. */
@Mixin(BaseSpawner.class)
public abstract class MixinSolarSpawner {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void solarPopulationLimit(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (SolarEvents.pauseGuardianSpawner(level, pos, (BaseSpawner) (Object) this)) ci.cancel();
    }
}
