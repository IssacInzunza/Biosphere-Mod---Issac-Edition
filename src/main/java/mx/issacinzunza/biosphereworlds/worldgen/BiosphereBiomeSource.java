package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.registry.ModBiomeSources;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.stream.Stream;

public final class BiosphereBiomeSource extends BiomeSource {
    public static final MapCodec<BiosphereBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(BiosphereBiomeSource::source),
            Biome.CODEC.fieldOf("void_biome").forGetter(BiosphereBiomeSource::voidBiome)
    ).apply(instance, BiosphereBiomeSource::new));

    private final BiomeSource source;
    private final Holder<Biome> voidBiome;

    public BiosphereBiomeSource(BiomeSource source, Holder<Biome> voidBiome) {
        this.source = source;
        this.voidBiome = voidBiome;
    }

    public BiomeSource source() {
        return source;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return ModBiomeSources.BIOSPHERES.get();
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(), Stream.of(voidBiome));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);
        return isInsideSphere(blockX, blockY, blockZ) ? source.getNoiseBiome(x, y, z, sampler) : voidBiome();
    }

    // Las busquedas heredadas consultan este wrapper en cada candidato y siguen
    // buscando si el mas cercano cae en el vacio. El codec historico se conserva.

    private static boolean isInsideSphere(int x, int y, int z) {
        return SphereLayout.current().isInside(x, y, z, -4);
    }

    public Holder<Biome> voidBiome() {
        return voidBiome;
    }
}
