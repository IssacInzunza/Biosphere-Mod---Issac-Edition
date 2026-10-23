package mx.issacinzunza.biosphereworlds.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChunkGeneratorStructureState.class)
public interface SolarStructureStateInvoker {
    @Invoker("<init>")
    static ChunkGeneratorStructureState biosphereworlds$create(RandomState random, BiomeSource biomes, long seed,
                                                              long ringsSeed, List<Holder<StructureSet>> sets) {
        throw new AssertionError();
    }
}
