package mx.issacinzunza.biosphereworlds.worldgen.solar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.stream.Stream;

public final class SolarBiomeSource extends BiomeSource {
    public static final MapCodec<SolarBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(s -> s.source),
            Biome.CODEC.fieldOf("void_biome").forGetter(s -> s.voidBiome),
            SolarSettings.CODEC.fieldOf("solar").forGetter(s -> s.settings)
    ).apply(i, SolarBiomeSource::new));
    private final BiomeSource source;
    private final Holder<Biome> voidBiome;
    private volatile SolarSettings settings;
    private volatile SolarLayout layout;
    private Holder<Biome> hotCentral;
    private Holder<Biome> coldCentral;
    private volatile Samplers climateSamplers;

    private record Samplers(Climate.Sampler original, Climate.Sampler hot, Climate.Sampler temperate, Climate.Sampler cold) { }

    public SolarBiomeSource(BiomeSource source, Holder<Biome> voidBiome, SolarSettings settings) {
        this.source = source;
        this.voidBiome = voidBiome;
        this.settings = settings;
    }

    public BiomeSource source() { return source; }
    public Holder<Biome> voidBiome() { return voidBiome; }
    public void bind(SolarLayout value, SolarSettings snapshot) {
        if (layout != null && layout != value) throw new IllegalStateException("Solar biome source rebound");
        settings = snapshot;
        hotCentral = source.possibleBiomes().stream().filter(b -> b.is(Biomes.BADLANDS)).findFirst().orElse(null);
        coldCentral = source.possibleBiomes().stream().filter(b -> b.is(Biomes.ICE_SPIKES)).findFirst().orElse(null);
        layout = value;
    }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(source.possibleBiomes().stream(), Stream.of(voidBiome)).distinct();
    }

    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<Biome> forced = forcedBiome(x, y, z);
        if (forced != null) return forced;
        return source.getNoiseBiome(x, y, z, climateSampler(x, y, z, sampler));
    }

    /** Null lets the real biome source (including TerraBlender) choose the biome. */
    public Holder<Biome> forcedBiome(int x, int y, int z) {
        SolarLayout current = layout;
        if (current == null) throw new IllegalStateException("Solar biome lookup before world seed binding");
        SolarLayout.Body body = current.bodyAt(QuartPos.toBlock(x), QuartPos.toBlock(y), QuartPos.toBlock(z), -4);
        if (body == null) return voidBiome;
        if (body.central() && settings.layoutVersion() >= 2) {
            Holder<Biome> central = settings.centralClimate() == SolarLayout.Climate.COLD ? coldCentral
                    : settings.centralClimate() == SolarLayout.Climate.HOT ? hotCentral : null;
            if (central != null) return central;
        }
        return null;
    }

    public Climate.Sampler climateSampler(int x, int y, int z, Climate.Sampler sampler) {
        if (!settings.climateEnabled() || !(source instanceof MultiNoiseBiomeSource)) return sampler;
        SolarLayout current = layout;
        if (current == null) throw new IllegalStateException("Solar biome lookup before world seed binding");
        SolarLayout.Body body = current.bodyAt(QuartPos.toBlock(x), QuartPos.toBlock(y), QuartPos.toBlock(z), -4);
        if (body == null) return sampler;
        Samplers cached = climateSamplers;
        if (cached == null || cached.original() != sampler) {
            cached = new Samplers(sampler, withTemperature(sampler, 0.8F), withTemperature(sampler, 0.0F), withTemperature(sampler, -0.8F));
            climateSamplers = cached;
        }
        SolarLayout.Climate climate = body.central() ? settings.centralClimate() : body.climate();
        return switch (climate) {
            case HOT -> cached.hot();
            case TEMPERATE -> cached.temperate();
            case COLD -> cached.cold();
        };
    }

    private static Climate.Sampler withTemperature(Climate.Sampler original, float temperature) {
        return new Climate.Sampler(DensityFunctions.constant(temperature), original.humidity(), original.continentalness(),
                original.erosion(), original.depth(), original.weirdness(), original.spawnTarget());
    }
}
