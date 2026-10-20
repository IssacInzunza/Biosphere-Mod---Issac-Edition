package mx.issacinzunza.biosphereworlds.worldgen.solar;

import com.mojang.serialization.MapCodec;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SolarWorldgen {
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, BiosphereWorlds.MODID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, BiosphereWorlds.MODID);
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<SolarChunkGenerator>> GENERATOR =
            GENERATORS.register("solar_systems", () -> SolarChunkGenerator.CODEC);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<SolarPlacement>> PLACEMENT =
            PLACEMENTS.register("solar_central", () -> () -> SolarPlacement.CODEC);

    private SolarWorldgen() { }
    public static void register(IEventBus bus) { GENERATORS.register(bus); PLACEMENTS.register(bus); }
}
