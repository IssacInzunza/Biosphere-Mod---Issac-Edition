package mx.issacinzunza.biosphereworlds.easteregg;

import java.lang.reflect.Method;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/** Optional Create integration, isolated from bytecode references to Create classes. */
final class ObserverLinks {
    private ObserverLinks() { }

    static Target find(ServerLevel level, BlockPos pos) {
        if (!ObserverEvents.applies(level) || !EasterEggConfig.ENABLED.get() || !level.getServer().isSameThread()) return null;
        if (!ModList.get().isLoaded("create")) return null;
        Api api = CreateApi.API;
        if (api == null) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var block = chunk.getBlockEntities().get(pos);
        if (!api.blockType.isInstance(block)) return null;
        try {
            if (api.receiver(block) != null) return new Target(block, api);
        } catch (ReflectiveOperationException incompatible) {
            BiosphereWorlds.LOGGER.debug("Observer: Create receiver unavailable at {}", pos, incompatible);
        }
        return null;
    }

    static final class Target {
        private final BlockEntity block;
        private final Api api;
        private boolean overridden;

        private Target(BlockEntity block, Api api) {
            this.block = block;
            this.api = api;
        }

        void update(ServerLevel level, int pulse) {
            if (!ObserverEvents.applies(level) || !level.getServer().isSameThread()) return;
            if (pulse < 0 && !overridden) return;
            var pos = block.getBlockPos();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            // Never touch a replacement block or force an unloaded receiver back into memory.
            if (chunk == null || block.isRemoved() || chunk.getBlockEntities().get(pos) != block) return;
            try {
                Object receiver = api.receiver(block); // Mode/frequencies may have changed during the encounter.
                if (receiver == null) {
                    overridden = false;
                    return;
                }
                int power = pulse >= 0 ? pulse : api.networkPower(level, receiver);
                api.setSignal.invoke(block, power);
                // Create's normal next block-entity tick updates the indicator and its neighbors.
                overridden = pulse >= 0;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                BiosphereWorlds.LOGGER.debug("Observer: Create signal update unavailable at {}", pos, unavailable);
            }
        }
    }

    private static final class CreateApi {
        private static final Api API = load();

        private static Api load() {
            try {
                // Public signatures verified in Create's 1.21.1 sources and the 6.0.8 release JAR.
                var block = Class.forName("com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity");
                var smart = Class.forName("com.simibubi.create.foundation.blockEntity.SmartBlockEntity");
                var behaviourType = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType");
                var link = Class.forName("com.simibubi.create.content.redstone.link.LinkBehaviour");
                var linkable = Class.forName("com.simibubi.create.content.redstone.link.IRedstoneLinkable");
                var handler = Class.forName("com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler");
                Object network = Class.forName("com.simibubi.create.Create").getField("REDSTONE_LINK_NETWORK_HANDLER").get(null);
                return new Api(block, smart.getMethod("getBehaviour", behaviourType), link.getField("TYPE").get(null),
                        linkable.getMethod("isListening"), linkable.getMethod("isAlive"), block.getMethod("setSignal", int.class),
                        handler.getMethod("getNetworkOf", LevelAccessor.class, linkable), network,
                        handler.getMethod("withinRange", linkable, linkable), linkable.getMethod("getTransmittedStrength"));
            } catch (ReflectiveOperationException | LinkageError incompatible) {
                BiosphereWorlds.LOGGER.warn("Observer: optional Create Redstone Link interference unavailable", incompatible);
                return null;
            }
        }
    }

    private record Api(Class<?> blockType, Method behaviour, Object type, Method listening, Method alive,
                       Method setSignal, Method networkOf, Object network, Method withinRange, Method transmitted) {
        Object receiver(BlockEntity block) throws ReflectiveOperationException {
            Object link = behaviour.invoke(block, type);
            return link != null && (boolean) listening.invoke(link) && (boolean) alive.invoke(link) ? link : null;
        }

        int networkPower(ServerLevel level, Object receiver) throws ReflectiveOperationException {
            int power = 0;
            // Reuse Create's frequency network and range rule. Restore current inputs, never a saved old signal.
            for (Object other : (Iterable<?>) networkOf.invoke(network, level, receiver)) {
                if ((boolean) alive.invoke(other) && (boolean) withinRange.invoke(null, receiver, other))
                    power = Math.max(power, (int) transmitted.invoke(other));
            }
            return Math.clamp(power, 0, 15);
        }
    }
}
