package mx.issacinzunza.biosphereworlds.density;

import mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** Adaptador runtime: conserva las operaciones clasicas usando la receta del mundo. */
public record ClassicDensityFunction(DensityFunction input, ClassicWorldSettings.Bound world,
                                    SphereEdgeOcean.Mode mode) implements DensityFunction {
    public static NoiseRouter adapt(NoiseRouter template, ClassicWorldSettings.Bound world) {
        return template.mapAll(function -> {
            if (function instanceof MultipleSpheresDistanceFunction) return new ClassicDensityFunction(DensityFunctions.zero(), world, null);
            if (function instanceof SphereEdgeOceanFunction ocean) return new ClassicDensityFunction(ocean.input(), world, ocean.mode());
            return function;
        });
    }
    @Override public double compute(FunctionContext context) {
        if (mode != null) {
            var ocean = world.settings().ocean();
            double original = input.compute(context);
            if (!ocean.enabled()) return original;
            return SphereEdgeOcean.transform(mode, true, ocean.width(), ocean.depth(), world.layout(),
                    context.blockX(), context.blockY(), context.blockZ(), original);
        }
        var sphere = world.layout().sphereContaining(context.blockX(), context.blockZ());
        double dx = context.blockX() - sphere.centerX();
        double dy = context.blockY() - sphere.centerY();
        double dz = context.blockZ() - sphere.centerZ();
        double distance = dx * dx + dy * dy + dz * dz;
        int radius = sphere.radius();
        return distance / (radius * radius);
    }
    @Override public void fillArray(double[] values, ContextProvider provider) {
        if (mode == null) {
            provider.fillAllDirectly(values, this);
            return;
        }
        input.fillArray(values, provider);
        var ocean = world.settings().ocean();
        if (!ocean.enabled()) return;
        for (int i = 0; i < values.length; i++) {
            var context = provider.forIndex(i);
            values[i] = SphereEdgeOcean.transform(mode, true, ocean.width(), ocean.depth(), world.layout(),
                    context.blockX(), context.blockY(), context.blockZ(), values[i]);
        }
    }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ClassicDensityFunction(input.mapAll(visitor), world, mode));
    }
    @Override public double minValue() {
        if (mode == null) return 0;
        if (!world.settings().ocean().enabled()) return input.minValue();
        return mode == SphereEdgeOcean.Mode.CLIMATE ? input.minValue() - SphereEdgeOcean.CLIMATE_BIAS
                : Math.min(input.minValue(), SphereEdgeOcean.FLOOR_MIN_PROFILE);
    }
    @Override public double maxValue() { return mode == null ? Double.POSITIVE_INFINITY : input.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new UnsupportedOperationException("Classic density adapters are runtime-only");
    }
}
