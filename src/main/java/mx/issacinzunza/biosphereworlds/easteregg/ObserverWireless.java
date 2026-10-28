package mx.issacinzunza.biosphereworlds.easteregg;

import java.lang.reflect.Method;
import java.util.List;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Optional CC bridge. Computer threads read only an immutable snapshot owned by their level. */
public final class ObserverWireless {
    private ObserverWireless() { }

    public interface Area {
        List<Vec3> biospheres$observerAreas();
        void biospheres$observerAreas(List<Vec3> centers);
    }

    public static void publish(ServerLevel level, List<Vec3> centers) {
        if (!ObserverEvents.applies(level) || !level.getServer().isSameThread()) return;
        ((Area) level).biospheres$observerAreas(EasterEggConfig.ENABLED.get() ? List.copyOf(centers) : List.of());
    }

    public static boolean blocked(Object receiver, Object packet) {
        if (!EasterEggConfig.ENABLED.get()) return false;
        var api = Binding.API;
        if (api == null) return false;
        try {
            var receiveLevel = (Level) api.receiverLevel.invoke(receiver);
            if (affected(receiveLevel, receiver, api.receiverPosition)) return true;
            Object sender = api.sender.invoke(packet);
            return affected((Level) api.senderLevel.invoke(sender), sender, api.senderPosition);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false; // Missing/incompatible optional API leaves CC's original delivery intact.
        }
    }

    private static boolean affected(Level level, Object endpoint, Method position) throws ReflectiveOperationException {
        if (!ObserverEvents.applies(level)) return false;
        var centers = ((Area) level).biospheres$observerAreas();
        if (centers.isEmpty()) return false;
        var point = (Vec3) position.invoke(endpoint);
        for (var center : centers)
            if (point.distanceToSqr(center) <= (double) ObserverRules.INTERFERENCE_RADIUS * ObserverRules.INTERFERENCE_RADIUS) return true;
        return false;
    }

    // Public CC API methods, bound lazily; CC classes never appear in a required JVM signature.
    private record Api(Method sender, Method receiverLevel, Method receiverPosition, Method senderLevel, Method senderPosition) { }
    private static final class Binding {
        private static final Api API = bind();
        private static Api bind() {
            try {
                var receiver = Class.forName("dan200.computercraft.api.network.PacketReceiver");
                var sender = Class.forName("dan200.computercraft.api.network.PacketSender");
                return new Api(Class.forName("dan200.computercraft.api.network.Packet").getMethod("sender"),
                        receiver.getMethod("getLevel"), receiver.getMethod("getPosition"),
                        sender.getMethod("getLevel"), sender.getMethod("getPosition"));
            } catch (ReflectiveOperationException | LinkageError exception) {
                BiosphereWorlds.LOGGER.warn("Observer wireless integration unavailable: {}", exception.toString());
                return null;
            }
        }
    }
}
