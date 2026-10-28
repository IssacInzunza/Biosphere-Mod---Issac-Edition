package mx.issacinzunza.biosphereworlds.easteregg;

import java.lang.reflect.Method;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Optional integration through public capabilities only. No optional mod classes in bytecode signatures. */
final class ObserverEnergy {
    private ObserverEnergy() { }

    static int drain(ServerLevel level, BlockPos pos, ObserverRules.DrainBudget budget) {
        // Caller already checked preset, toggles, loaded chunk and encounter budget.
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return 0;
        if (ModList.get().isLoaded("ae2")) {
            int ae = Ae2.API == null ? 0 : Ae2.API.drain(level, pos, budget);
            if (ae > 0) return ae;
        }
        // IE, Crafts & Additions and Mekanism can expose FE. Input-only faces are respected.
        for (Direction side : Direction.values()) {
            var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
            if (storage == null || !storage.canExtract()) continue;
            int request = budget.request(pos, storage.getEnergyStored());
            if (request == 0) return 0;
            int actual = Math.clamp(storage.extractEnergy(request, false), 0, request);
            budget.consumed(pos, actual);
            return actual; // One face per block, not six charges against the same battery.
        }
        return 0;
    }

    private static final class Ae2 {
        private static final Api API = load();

        @SuppressWarnings("unchecked")
        private static Api load() {
            try {
                // Verified against AE2 neoforge/v19.2.17 public API (Minecraft 1.21.1).
                var capability = (BlockCapability<Object, Void>) Class.forName("appeng.api.AECapabilities")
                        .getField("IN_WORLD_GRID_NODE_HOST").get(null);
                var host = Class.forName("appeng.api.networking.IInWorldGridNodeHost");
                var node = Class.forName("appeng.api.networking.IGridNode");
                var grid = Class.forName("appeng.api.networking.IGrid");
                var energy = Class.forName("appeng.api.networking.energy.IEnergyService");
                var source = Class.forName("appeng.api.networking.energy.IEnergySource");
                var action = Class.forName("appeng.api.config.Actionable");
                var multiplier = Class.forName("appeng.api.config.PowerMultiplier");
                return new Api(capability, host.getMethod("getGridNode", Direction.class), node.getMethod("getGrid"),
                        grid.getMethod("getEnergyService"), energy.getMethod("getStoredPower"),
                        source.getMethod("extractAEPower", double.class, action, multiplier),
                        action.getField("MODULATE").get(null), multiplier.getField("ONE").get(null));
            } catch (ReflectiveOperationException | LinkageError incompatible) {
                BiosphereWorlds.LOGGER.warn("Observer: optional AE2 drain unavailable; public API does not match 19.2.17", incompatible);
                return null;
            }
        }
    }

    private record Api(BlockCapability<Object, Void> capability, Method gridNode, Method grid, Method energy,
                       Method stored, Method extract, Object modulate, Object one) {
        int drain(ServerLevel level, BlockPos pos, ObserverRules.DrainBudget budget) {
            Object host = level.getCapability(capability, pos, null);
            if (host == null) return 0;
            try {
                for (Direction side : Direction.values()) {
                    Object node = gridNode.invoke(host, side);
                    if (node == null) continue;
                    Object network = grid.invoke(node);
                    Object service = energy.invoke(network);
                    double available = ((Number) stored.invoke(service)).doubleValue();
                    int request = budget.request(network, (long) Math.max(0, available * 2));
                    if (request == 0) return 0;
                    double actual = ((Number) extract.invoke(service, request / 2.0, modulate, one)).doubleValue();
                    int spent = Math.clamp((int) Math.ceil(actual * 2), 0, request);
                    budget.consumed(network, spent);
                    return spent; // Same grid is the same budget key even through different cables/machines.
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                // A node can be uninitialized or removed between machine updates. Never edit its NBT.
                BiosphereWorlds.LOGGER.debug("Observer: optional AE2 target unavailable at {}", pos, unavailable);
            }
            return 0;
        }
    }
}
