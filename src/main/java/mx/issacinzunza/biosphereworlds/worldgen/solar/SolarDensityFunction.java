package mx.issacinzunza.biosphereworlds.worldgen.solar;

import mx.issacinzunza.biosphereworlds.density.MultipleSpheresDistanceFunction;
import mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean;
import mx.issacinzunza.biosphereworlds.density.SphereEdgeOceanFunction;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * Runtime adapter of the original Biospheres router to per-world Solar geometry.
 * It is never serialized; the generator saves the original noise-settings holder.
 * @param input wrapped function, visited recursively by mapAll
 * @param layout immutable world geometry with bounded snapshot caches
 * @param environment saved ocean options, independent of global configuration
 * @param kind distance, terrain-floor, or climate adaptation
 */
public record SolarDensityFunction(DensityFunction input, SolarLayout layout,
                                   SolarSettings.Environment environment, Kind kind) implements DensityFunction {
    public enum Kind { DISTANCE, FLOOR, CLIMATE }

    /**
     * Replaces only the original Biospheres distance/ocean wrappers in a noise template.
     * @param template original router, left unchanged
     * @param layout geometry belonging to the bound Solar world
     * @param environment saved Solar terrain options
     * @return router with runtime Solar wrappers and all other functions preserved
     */
    public static NoiseRouter adapt(NoiseRouter template, SolarLayout layout, SolarSettings.Environment environment) {
        return template.mapAll(function -> {
            if (function instanceof MultipleSpheresDistanceFunction) {
                return new SolarDensityFunction(DensityFunctions.zero(), layout, environment, Kind.DISTANCE);
            }
            if (function instanceof SphereEdgeOceanFunction ocean) {
                return new SolarDensityFunction(ocean.input(), layout, environment,
                        ocean.mode() == SphereEdgeOcean.Mode.FLOOR ? Kind.FLOOR : Kind.CLIMATE);
            }
            return function;
        });
    }

    @Override public double compute(FunctionContext context) {
        double original = input.compute(context);
        if (kind != Kind.DISTANCE && !environment.edgeOceanEnabled()) return original;
        var body = layout.bodyAt(context.blockX(), context.blockY(), context.blockZ(), 0);
        if (body == null) return kind == Kind.DISTANCE ? 2.0 : original;
        double dx = context.blockX() - body.x();
        double dy = context.blockY() - body.y();
        double dz = context.blockZ() - body.z();
        if (kind == Kind.DISTANCE) return (dx * dx + dy * dy + dz * dz) / ((double) body.radius() * body.radius());
        // Keep the sun's independent magma/ice profile and its complete Stronghold envelope.
        if (body.central()) return original;
        double weight = SphereEdgeOcean.weight(body.radius(), dx, dy, dz, environment.edgeOceanWidth() / 100.0);
        return SphereEdgeOcean.transformWeighted(kind == Kind.FLOOR ? SphereEdgeOcean.Mode.FLOOR : SphereEdgeOcean.Mode.CLIMATE,
                environment.edgeOceanDepth(), context.blockY(), original, weight);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SolarDensityFunction(input.mapAll(visitor), layout, environment, kind));
    }

    @Override public double minValue() {
        if (kind == Kind.DISTANCE) return 0;
        if (!environment.edgeOceanEnabled()) return input.minValue();
        return kind == Kind.CLIMATE ? input.minValue() - SphereEdgeOcean.CLIMATE_BIAS
                : Math.min(input.minValue(), SphereEdgeOcean.FLOOR_MIN_PROFILE);
    }

    @Override public double maxValue() { return kind == Kind.DISTANCE ? 2 : input.maxValue(); }

    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        // Only the unmodified template holder and Solar snapshot are persisted by the generator.
        throw new UnsupportedOperationException("Solar density adapters are runtime-only");
    }
}
