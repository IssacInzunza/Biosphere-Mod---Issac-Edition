package cn.mlus.neobiosphere.registry;

import cn.mlus.neobiosphere.Neobiosphere;
import cn.mlus.neobiosphere.worldgen.BiosphereBiomeSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBiomeSources {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Neobiosphere.MODID);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<BiosphereBiomeSource>> BIOSPHERES =
            BIOME_SOURCES.register("biospheres", () -> BiosphereBiomeSource.CODEC);

    private ModBiomeSources() {
    }
}
