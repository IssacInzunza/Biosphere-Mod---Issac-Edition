package mx.issacinzunza.biosphereworlds.registry;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereBiomeSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBiomeSources {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, BiosphereWorlds.MODID);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<BiosphereBiomeSource>> BIOSPHERES =
            BIOME_SOURCES.register("biospheres", () -> BiosphereBiomeSource.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource>> SOLAR =
            BIOME_SOURCES.register("solar_systems", () -> mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource.CODEC);

    private ModBiomeSources() {
    }
}
