package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarGuardians;
import org.spongepowered.asm.mixin.Shadow;
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
    @Shadow private int spawnDelay;
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void solarPopulationLimit(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        var action = SolarGuardians.beforeTick(level, pos, (BaseSpawner) (Object) this, spawnDelay);
        if (action == SolarGuardians.TickAction.RETRY) spawnDelay = 20;
        if (action != SolarGuardians.TickAction.VANILLA) ci.cancel();
    }
}
