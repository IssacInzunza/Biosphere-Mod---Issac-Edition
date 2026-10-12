package cn.mlus.biosphereworlds.density;

import cn.mlus.biosphereworlds.config.SphereConfig;
import cn.mlus.biosphereworlds.worldgen.SphereLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

/**
 * Oceano exterior opcional de las esferas.
 *
 * <p>Envuelve una funcion de densidad y le aplica, cerca del cascaron, la
 * transformacion de {@link SphereEdgeOcean}. Se registra como
 * {@code biosphereworlds:sphere_edge_ocean} y se usa en tres sitios del router:
 * el suelo de {@code final_density}, el de
 * {@code initial_density_without_jaggedness} (modo {@code floor}) y la
 * continentalness que alimenta el {@code Climate.Sampler} (modo {@code climate}).</p>
 *
 * <p>Todos los ajustes viven en {@link SphereConfig} y se leen en cada llamada,
 * igual que {@link MultipleSpheresDistanceFunction}. Con
 * {@code edge_ocean_enabled = false}, {@link #compute}, {@link #fillArray} y los
 * limites devuelven el valor de la funcion envuelta sin tocarlo.</p>
 *
 * <p>Aviso: eso solo es un no-op real si {@link #mapAll} recorre el subarbol.
 * {@code NoiseChunk} usa {@code mapAll} para sustituir los marcadores
 * ({@code interpolated}, {@code cache_2d}, ...) y para dar sampler a los nodos
 * {@code minecraft:noise}; un {@code NoiseHolder} sin visitar devuelve {@code 0.0}
 * (DensityFunction.NoiseHolder#getValue), asi que dejarlo sin recorrer cambia el
 * terreno aunque la opcion este apagada.</p>
 */
public final class SphereEdgeOceanFunction implements DensityFunction {

    public static final Codec<SphereEdgeOcean.Mode> MODE_CODEC = Codec.STRING.comapFlatMap(
            name -> switch (name) {
                case "floor" -> DataResult.success(SphereEdgeOcean.Mode.FLOOR);
                case "climate" -> DataResult.success(SphereEdgeOcean.Mode.CLIMATE);
                default -> DataResult.error(() -> "Unknown sphere edge ocean mode: " + name);
            },
            mode -> mode == SphereEdgeOcean.Mode.FLOOR ? "floor" : "climate");

    public static final MapCodec<SphereEdgeOceanFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(SphereEdgeOceanFunction::input),
            MODE_CODEC.fieldOf("mode").forGetter(SphereEdgeOceanFunction::mode)
    ).apply(instance, SphereEdgeOceanFunction::new));

    public static final KeyDispatchDataCodec<SphereEdgeOceanFunction> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);

    private final DensityFunction input;
    private final SphereEdgeOcean.Mode mode;

    public SphereEdgeOceanFunction(DensityFunction input, SphereEdgeOcean.Mode mode) {
        this.input = input;
        this.mode = mode;
    }

    public DensityFunction input() {
        return input;
    }

    public SphereEdgeOcean.Mode mode() {
        return mode;
    }

    @Override
    public double compute(FunctionContext context) {
        if (!SphereConfig.EDGE_OCEAN_ENABLED.get()) {
            return input.compute(context);
        }
        return SphereEdgeOcean.transform(mode, true,
                SphereConfig.EDGE_OCEAN_WIDTH.get(), SphereConfig.EDGE_OCEAN_DEPTH.get(),
                SphereLayout.current(), context.blockX(), context.blockY(), context.blockZ(),
                input.compute(context));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        input.fillArray(array, provider);
        if (!SphereConfig.EDGE_OCEAN_ENABLED.get()) {
            return;
        }
        double widthPercent = SphereConfig.EDGE_OCEAN_WIDTH.get();
        int depth = SphereConfig.EDGE_OCEAN_DEPTH.get();
        SphereLayout layout = SphereLayout.current();
        for (int i = 0; i < array.length; i++) {
            FunctionContext context = provider.forIndex(i);
            array[i] = SphereEdgeOcean.transform(mode, true, widthPercent, depth, layout,
                    context.blockX(), context.blockY(), context.blockZ(), array[i]);
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SphereEdgeOceanFunction(this.input.mapAll(visitor), this.mode));
    }

    @Override
    public double minValue() {
        if (!SphereConfig.EDGE_OCEAN_ENABLED.get()) {
            return input.minValue();
        }
        if (mode == SphereEdgeOcean.Mode.CLIMATE) {
            return input.minValue() - SphereEdgeOcean.CLIMATE_BIAS;
        }
        return Math.min(input.minValue(), SphereEdgeOcean.FLOOR_MIN_PROFILE);
    }

    @Override
    public double maxValue() {
        // Ninguna de las dos transformaciones sube el valor: restar sesgo baja, y el
        // minimo con el perfil nunca supera la entrada.
        return input.maxValue();
    }

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}
