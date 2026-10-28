package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.easteregg.ObserverWireless;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CC:Tweaked 1.21.1 WirelessNetwork.java:38; wired delivery is a different implementation. */
@Pseudo
@Mixin(targets = "dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork", remap = false)
public abstract class MixinObserverWireless {
    @Inject(method = "tryTransmit", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void observerInterference(@Coerce Object receiver, @Coerce Object packet,
                                             double range, boolean interdimensional, CallbackInfo ci) {
        if (ObserverWireless.blocked(receiver, packet)) ci.cancel();
    }
}
