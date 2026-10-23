package mx.issacinzunza.biosphereworlds.feature;

import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Conserva la nieve vanilla del bioma vacio solo en los dos presets del mod. */
public final class SphereSnowFeature extends SnowAndFreezeFeature {
    public SphereSnowFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var generator = context.chunkGenerator();
        if (!BiosphereWorldgen.isBiosphereGenerator(generator) && !(generator instanceof SolarChunkGenerator)) return false;
        return super.place(context);
    }
}
