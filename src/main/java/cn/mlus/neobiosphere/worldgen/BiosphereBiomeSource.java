package cn.mlus.neobiosphere.worldgen;

import cn.mlus.neobiosphere.carver.SphereCarver;
import cn.mlus.neobiosphere.registry.ModBiomeSources;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.util.RandomSource;

import java.util.Set;
import java.util.function.Predicate;
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
        return source.possibleBiomes().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<Biome> biome = source.getNoiseBiome(x, y, z, sampler);
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);
        return isInsideSphere(blockX, blockY, blockZ) ? biome : voidBiome();
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int radius,
                                                               Predicate<Holder<Biome>> predicate,
                                                               RandomSource random, Climate.Sampler sampler) {
        Pair<BlockPos, Holder<Biome>> result = source.findBiomeHorizontal(x, y, z, radius, predicate, random, sampler);
        return result != null && isInsideSphere(result.getFirst().getX(), result.getFirst().getY(), result.getFirst().getZ())
            ? result : null;
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int horizontalRadius,
                                                               int verticalRadius, Predicate<Holder<Biome>> predicate,
                                                               RandomSource random, boolean useFuzzy,
                                                               Climate.Sampler sampler) {
        Pair<BlockPos, Holder<Biome>> result = source.findBiomeHorizontal(x, y, z, horizontalRadius, verticalRadius,
            predicate, random, useFuzzy, sampler);
        return result != null && isInsideSphere(result.getFirst().getX(), result.getFirst().getY(), result.getFirst().getZ())
            ? result : null;
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos center, int horizontalSearchBlockCount,
                                                              int verticalSearchBlockCount, int step,
                                                              Predicate<Holder<Biome>> predicate,
                                                              Climate.Sampler sampler, LevelReader level) {
        Pair<BlockPos, Holder<Biome>> result = source.findClosestBiome3d(center, horizontalSearchBlockCount,
            verticalSearchBlockCount, step, predicate, sampler, level);
        return result != null && isInsideSphere(result.getFirst().getX(), result.getFirst().getY(), result.getFirst().getZ())
            ? result : null;
    }

    private static boolean isInsideSphere(int x, int y, int z) {
        return SphereCarver.isInsideAnySphere(x, y, z, -4);
    }

    public Holder<Biome> voidBiome() {
        return voidBiome;
    }
}
