package mx.issacinzunza.biosphereworlds.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

public class ModBiomeAccess {
    public static volatile HolderGetter<Biome> LOOKUP;

    /**
     * The {@code the_void} holder taken from the live server biome registry.
     *
     * <p>The lookup captured from {@code MultiNoiseBiomeSourceParameterList} comes
     * from a codec decode and may hold unbound references in a modpack. This holder
     * is resolved on server start, where the registry is frozen, so it is always
     * bound.</p>
     */
    private static volatile Holder<Biome> voidBiome;

    public static void register(HolderGetter<Biome> lookup) {
        LOOKUP = lookup;
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Registry<Biome> biomes = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
        voidBiome = biomes.getHolderOrThrow(Biomes.THE_VOID);
    }

    /** Bound {@code the_void} holder, or the codec lookup as a fallback. */
    public static Holder<Biome> voidBiome() {
        Holder<Biome> current = voidBiome;
        if (current != null && current.isBound()) {
            return current;
        }
        HolderGetter<Biome> lookup = LOOKUP;
        return lookup == null ? null : lookup.getOrThrow(Biomes.THE_VOID);
    }
}
