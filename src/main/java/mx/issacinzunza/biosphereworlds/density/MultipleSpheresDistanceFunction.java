package mx.issacinzunza.biosphereworlds.density;

import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record MultipleSpheresDistanceFunction(
        int spacing,
        int radius,
        int centerY
) implements DensityFunction.SimpleFunction {

    public static final MapCodec<MultipleSpheresDistanceFunction> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.fieldOf("spacing").forGetter(MultipleSpheresDistanceFunction::spacing),
                    Codec.INT.fieldOf("radius").forGetter(MultipleSpheresDistanceFunction::radius),
                    Codec.INT.fieldOf("center_y").forGetter(MultipleSpheresDistanceFunction::centerY)
            ).apply(instance, MultipleSpheresDistanceFunction::new));
    public static final KeyDispatchDataCodec<MultipleSpheresDistanceFunction> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);

    @Override
    public double compute(FunctionContext context) {
        SphereLayout layout = SphereLayout.current();

        // Calculate nearest grid point
        SphereLayout.Sphere sphere = layout.sphereContaining(context.blockX(), context.blockZ());

        // Calculate distance to nearest sphere center
        double dx = context.blockX() - sphere.centerX();
        double dy = context.blockY() - sphere.centerY();
        double dz = context.blockZ() - sphere.centerZ();

        double distance = dx * dx + dy * dy + dz * dz;
        int radius = sphere.radius();
        return distance / (radius * radius); // return value from 0 to 1, 0 = center, >1 = outside
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 2.0; // possible max value
    }

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}
