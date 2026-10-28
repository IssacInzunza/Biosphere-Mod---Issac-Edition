package mx.issacinzunza.biosphereworlds.mixin;

import java.util.List;
import mx.issacinzunza.biosphereworlds.easteregg.ObserverWireless;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Publishes immutable interference zones without exposing mutable level attachments to CC threads. */
@Mixin(ServerLevel.class)
public abstract class MixinObserverWirelessLevel implements ObserverWireless.Area {
    @Unique private volatile List<Vec3> biospheres$observerAreas = List.of();

    @Override public List<Vec3> biospheres$observerAreas() { return biospheres$observerAreas; }
    @Override public void biospheres$observerAreas(List<Vec3> centers) { biospheres$observerAreas = centers; }
}
