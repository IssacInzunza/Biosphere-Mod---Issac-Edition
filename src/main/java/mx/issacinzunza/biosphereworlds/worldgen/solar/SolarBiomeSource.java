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
        SolarLayout current = layout;
        if (current == null) throw new IllegalStateException("Solar biome lookup before world seed binding");
        SolarLayout.Body body = current.bodyAt(QuartPos.toBlock(x), QuartPos.toBlock(y), QuartPos.toBlock(z), -4);
        if (body == null) return voidBiome;
        if (body.central() && settings.layoutVersion() >= 2) {
            Holder<Biome> central = settings.centralClimate() == SolarLayout.Climate.COLD ? coldCentral
                    : settings.centralClimate() == SolarLayout.Climate.HOT ? hotCentral : null;
            if (central != null) return central;
        }
        if (!settings.climateEnabled() || !(source instanceof MultiNoiseBiomeSource multi)) {
            return source.getNoiseBiome(x, y, z, sampler);
        }
        Climate.TargetPoint point = sampler.sample(x, y, z);
        SolarLayout.Climate climate = body.central() ? settings.centralClimate() : body.climate();
        float temperature = switch (climate) {
            case HOT -> 0.8F;
            case TEMPERATE -> 0.0F;
            case COLD -> -0.8F;
        };
        return multi.getNoiseBiome(new Climate.TargetPoint(Climate.quantizeCoord(temperature), point.humidity(),
                point.continentalness(), point.erosion(), point.depth(), point.weirdness()));
    }
}
