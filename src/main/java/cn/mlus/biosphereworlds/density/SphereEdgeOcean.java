package cn.mlus.biosphereworlds.density;

import cn.mlus.biosphereworlds.worldgen.SphereLayout;

/**
 * Matematica pura del oceano exterior de las esferas.
 *
 * <p>Calcula el peso {@code b} de la banda cercana al cascaron y aplica la
 * transformacion sobre el valor de una funcion de densidad. No depende de
 * Minecraft ni de {@code SphereConfig}: recibe la instantanea de
 * {@link SphereLayout} y los valores del ajuste como argumentos. Es inmutable y
 * segura entre hilos porque no guarda estado.</p>
 *
 * <p>Con {@code enabled == false} la transformacion devuelve el valor de entrada
 * sin tocarlo, bit a bit igual.</p>
 */
public final class SphereEdgeOcean {

    /**
     * Nivel del mar de {@code noise_settings/biospheres.json}. Debe coincidir con su
     * campo {@code sea_level}; si ese valor cambia, hay que cambiarlo aqui.
     */
    public static final int SEA_LEVEL = 63;

    /**
     * Sesgo maximo de continentalness contra el cascaron, en unidades de clima. La
     * tabla de biomas de Overworld empieza a elegir oceano por debajo de {@code -0.11}
     * y oceano profundo por debajo de {@code -0.455}.
     */
    public static final double CLIMATE_BIAS = 0.5;

    /** Pendiente del perfil de suelo, en densidad por bloque. */
    public static final double FLOOR_SHARPNESS = 40.0;

    /**
     * Limite inferior del perfil de suelo. Solo se recorta por abajo, para que
     * {@code minValue} siga acotado; por arriba el perfil crece sin tope y
     * {@code min(input, perfil)} devuelve la entrada sin alterarla, de modo que el
     * terreno que ya estaba por debajo del suelo no cambia en absoluto.
     */
    public static final double FLOOR_MIN_PROFILE = -1.0;

    /** Que hace la transformacion con el valor de entrada. */
    public enum Mode {
        /** Baja el terreno hasta un suelo plano en {@code sea_level - depth}. */
        FLOOR,
        /** Empuja la continentalness hacia oceano, solo para el Climate.Sampler. */
        CLIMATE
    }

    private SphereEdgeOcean() {
    }

    /**
     * Peso {@code b} de la banda, en {@code [0,1]}.
     *
     * <p>{@code s} es la distancia al centro dividida por el radio de la esfera que
     * contiene la posicion. Vale {@code 0} hasta {@code 1 - width} (interior intacto),
     * sube con un smoothstep de pendiente nula en ambos extremos y vale {@code 1}
     * desde la cara interior del cascaron hasta el borde. Fuera de la esfera vale
     * {@code 0}, de modo que el hueco exterior no se toca.</p>
     *
     * @param widthFraction ancho de la banda como fraccion del radio, en {@code [0,1]}
     */
    public static double weight(SphereLayout layout, int blockX, int blockY, int blockZ, double widthFraction) {
        SphereLayout.Sphere sphere = layout.sphereContaining(blockX, blockZ);
        int radius = sphere.radius();
        if (radius <= 0) {
            return 0.0;
        }

        double dx = blockX - sphere.centerX();
        double dy = blockY - layout.params().centerY();
        double dz = blockZ - sphere.centerZ();
        double s = Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;

        if (s >= 1.0) {
            return 0.0;
        }
        double sInner = 1.0 - widthFraction;
        if (s <= sInner) {
            return 0.0;
        }
        double sEdge = (radius - SphereLayout.SHELL_THICKNESS) / radius;
        if (s >= sEdge) {
            return 1.0;
        }
        double t = (s - sInner) / (sEdge - sInner);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Aplica la transformacion sobre {@code inputValue}.
     *
     * @param enabled       si es {@code false} devuelve {@code inputValue} sin tocarlo
     * @param widthPercent  ancho de la banda como porcentaje del radio, en {@code [0,100]}
     * @param depth         profundidad del suelo por debajo del nivel del mar, en bloques
     */
    public static double transform(Mode mode, boolean enabled, double widthPercent, int depth,
                                   SphereLayout layout, int blockX, int blockY, int blockZ,
                                   double inputValue) {
        if (!enabled) {
            return inputValue;
        }

        double b = weight(layout, blockX, blockY, blockZ, widthPercent / 100.0);
        if (b == 0.0) {
            return inputValue;
        }

        if (mode == Mode.CLIMATE) {
            return inputValue - b * CLIMATE_BIAS;
        }

        // Suelo plano: densidad solida por debajo de floorY y aire por encima. El minimo
        // con el valor original conserva cuevas y valles que ya estaban por debajo.
        double floorY = SEA_LEVEL - depth;
        double profile = Math.max(FLOOR_MIN_PROFILE, (floorY - blockY) * FLOOR_SHARPNESS);
        double clamped = Math.min(inputValue, profile);
        return inputValue + b * (clamped - inputValue);
    }
}
