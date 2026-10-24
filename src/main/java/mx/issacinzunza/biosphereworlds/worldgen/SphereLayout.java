package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Geometria compartida de las esferas de Biosphere Worlds.
 *
 * <p>Esta clase es la unica fuente de verdad para: la celda de la cuadricula a la
 * que pertenece una coordenada, el centro de la esfera de una celda, su radio, la
 * prueba de pertenencia y la distancia al borde. Sustituye a los calculos que antes
 * estaban repartidos entre {@code SphereCarver}, {@code SphereBridgeCarver},
 * {@code SphereUtil}, {@code BiosphereBiomeSource}, {@code MultipleSpheresDistanceFunction}
 * y varios mixins.</p>
 *
 * <p>Los valores se calculan con exactamente las mismas operaciones que el codigo
 * anterior, para que los mundos se generen igual. En particular el radio conserva la
 * aritmetica {@code int} con desbordamiento (incluido el caso en que
 * {@code Math.abs(Integer.MIN_VALUE)} es negativo) y los centros conservan la
 * multiplicacion {@code int * int} antes de ensancharse a {@code double}.</p>
 *
 * <p>Es inmutable y segura entre hilos: todos los campos son {@code final}, la
 * unica mutacion es rellenar la cache por celda con objetos inmutables, y el
 * resultado no depende nunca del contenido de la cache. Los dos records son
 * inmutables, asi que una carrera al escribir la misma celda solo puede publicar
 * un objeto equivalente.</p>
 *
 * <p>Los parametros se toman de una instantanea de {@link SphereConfig}: si la
 * configuracion cambia, {@link #current()} publica una instancia nueva con cache
 * nueva, de modo que nunca se devuelven esferas calculadas con parametros viejos.</p>
 */
public final class SphereLayout {

    /** Grosor del cascaron, en bloques. Valor historico de {@code SphereCarver}. */
    public static final double SHELL_THICKNESS = 2.0;

    /**
     * Seleccion vertical del cascaron; el ecuador pertenece a la mitad superior.
     * No comprueba pertenencia a la esfera ni decide la contencion de agua.
     */
    public static boolean shellHemisphereEnabled(int y, double centerY, boolean upperEnabled, boolean lowerEnabled) {
        return y >= centerY ? upperEnabled : lowerEnabled;
    }

    /**
     * Banda interior del forro, con los limites radiales del carver clasico.
     * Recibe medidas precalculadas y su techo propio; no depende del vidrio.
     */
    public static boolean bedrockLinerContains(boolean enabled, int y, double ceilingY, double distanceSq,
                                               double shellInnerRadiusSq, double linerInnerRadiusSq) {
        return enabled && y <= ceilingY
                && distanceSq >= linerInnerRadiusSq && distanceSq < shellInnerRadiusSq;
    }

    /** Tamano de la cache por celda: {@code 1 << CACHE_BITS} entradas. */
    private static final int CACHE_BITS = 12;
    private static final int CACHE_SIZE = 1 << CACHE_BITS;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private static volatile SphereLayout current;

    private final Params params;
    private final AtomicReferenceArray<Sphere> cache = new AtomicReferenceArray<>(CACHE_SIZE);

    /**
     * Cache por celda de los satelites. Es {@code null} cuando los satelites estan
     * apagados, de modo que esa ruta no reserva memoria ni calcula nada.
     */
    private final AtomicReferenceArray<SatelliteCell> satelliteCache;

    public SphereLayout(Params params) {
        this.params = params;
        this.satelliteCache = params.satellitesEnabled()
                ? new AtomicReferenceArray<>(CACHE_SIZE)
                : null;
    }

    /** Instantanea de los parametros de la cuadricula. Inmutable. */
    public record Params(int spacing, int centerY, int minRadius, int maxRadius,
                         boolean satellitesEnabled, int satellitesMaxCount,
                         int satellitesMinSizePercent, int satellitesMaxSizePercent,
                         int satellitesMinGapPercent, int satellitesMaxGapPercent,
                         int satellitesMinGapBlocks,
                         int satellitesMinElevationDegrees, int satellitesMaxElevationDegrees) {

        public Params {
            if (spacing <= 0) {
                throw new IllegalArgumentException("spacing must be > 0, got " + spacing);
            }
            satellitesMaxCount = Math.max(0, satellitesMaxCount);
            satellitesMinSizePercent = clamp(satellitesMinSizePercent, 1, 100);
            satellitesMaxSizePercent = clamp(satellitesMaxSizePercent, 1, 100);
            if (satellitesMinSizePercent > satellitesMaxSizePercent) {
                satellitesMinSizePercent = satellitesMaxSizePercent;
            }
            satellitesMinGapPercent = clamp(satellitesMinGapPercent, 0, 1000);
            satellitesMaxGapPercent = clamp(satellitesMaxGapPercent, 0, 1000);
            if (satellitesMinGapPercent > satellitesMaxGapPercent) {
                satellitesMinGapPercent = satellitesMaxGapPercent;
            }
            satellitesMinGapBlocks = Math.max(0, satellitesMinGapBlocks);
            satellitesMinElevationDegrees = clamp(satellitesMinElevationDegrees, 0, 90);
            satellitesMaxElevationDegrees = clamp(satellitesMaxElevationDegrees, 0, 90);
            if (satellitesMinElevationDegrees > satellitesMaxElevationDegrees) {
                satellitesMinElevationDegrees = satellitesMaxElevationDegrees;
            }
        }

        /**
         * Parametros sin satelites. Es el constructor que usan las pruebas de
         * equivalencia: con {@code satellitesEnabled == false} toda la geometria
         * nueva se corta antes de tocar la cache de satelites.
         */
        public Params(int spacing, int centerY, int minRadius, int maxRadius) {
            this(spacing, centerY, minRadius, maxRadius, false, 0, 8, 12, 25, 75, 16, 30, 55);
        }
    }

    /**
     * Una esfera concreta de la cuadricula. Inmutable.
     *
     * <p>{@code centerY} es propio de cada esfera. Hoy todas las principales usan el
     * mismo valor de configuracion, pero la prueba de pertenencia lee el centro de la
     * esfera y no el global, para que los satelites puedan tener su propia altura.</p>
     */
    public record Sphere(int cellX, int cellZ, double centerX, double centerY, double centerZ, int radius) {
    }

    /**
     * Satelite de una esfera principal. Inmutable. {@code seed} sale de la celda,
     * el indice y el intento de colocacion, no de la posicion en coma flotante: es
     * la base estable del mineral y del hash por bloque.
     */
    public record Satellite(double centerX, double centerY, double centerZ, int radius, long seed) {
    }

    private record SatelliteCell(int cellX, int cellZ, List<Satellite> satellites) {
    }

    // ------------------------------------------------------------------
    // Construccion desde la configuracion
    // ------------------------------------------------------------------

    /**
     * Devuelve la instancia que refleja la configuracion actual, creandola o
     * reemplazandola solo cuando algun parametro cambia. La comparacion usa el
     * {@link Params} ya normalizado, de modo que tambien detecta los cambios de las
     * opciones de satelites.
     */
    public static SphereLayout current() {
        int spacing = SphereConfig.SPACING.get().intValue();
        int centerY = SphereConfig.CENTER_Y.get().intValue();
        int minRadius = SphereConfig.MIN_RADIUS.get().intValue();
        int maxRadius = SphereConfig.RADIUS.get().intValue();
        validateConfiguredRadii(minRadius, maxRadius);
        boolean satellitesEnabled = SphereConfig.SATELLITES_ENABLED.get();
        int satellitesMaxCount = Math.max(0, SphereConfig.SATELLITES_MAX_COUNT.get());
        int satellitesMinSizePercent = clamp(SphereConfig.SATELLITES_MIN_SIZE_PERCENT.get(), 1, 100);
        int satellitesMaxSizePercent = clamp(SphereConfig.SATELLITES_MAX_SIZE_PERCENT.get(), 1, 100);
        if (satellitesMinSizePercent > satellitesMaxSizePercent) {
            satellitesMinSizePercent = satellitesMaxSizePercent;
        }
        int satellitesMinGapPercent = clamp(SphereConfig.SATELLITES_MIN_GAP_PERCENT.get(), 0, 1000);
        int satellitesMaxGapPercent = clamp(SphereConfig.SATELLITES_MAX_GAP_PERCENT.get(), 0, 1000);
        if (satellitesMinGapPercent > satellitesMaxGapPercent) {
            satellitesMinGapPercent = satellitesMaxGapPercent;
        }
        int satellitesMinGapBlocks = Math.max(0, SphereConfig.SATELLITES_MIN_GAP_BLOCKS.get());
        int satellitesMinElevation = clamp(SphereConfig.SATELLITES_MIN_ELEVATION_DEGREES.get(), 0, 90);
        int satellitesMaxElevation = clamp(SphereConfig.SATELLITES_MAX_ELEVATION_DEGREES.get(), 0, 90);
        if (satellitesMinElevation > satellitesMaxElevation) {
            satellitesMinElevation = satellitesMaxElevation;
        }

        SphereLayout layout = current;
        if (layout != null) {
            Params p = layout.params;
            if (p.spacing() == spacing && p.centerY() == centerY
                    && p.minRadius() == minRadius && p.maxRadius() == maxRadius
                    && p.satellitesEnabled() == satellitesEnabled
                    && p.satellitesMaxCount() == satellitesMaxCount
                    && p.satellitesMinSizePercent() == satellitesMinSizePercent
                    && p.satellitesMaxSizePercent() == satellitesMaxSizePercent
                    && p.satellitesMinGapPercent() == satellitesMinGapPercent
                    && p.satellitesMaxGapPercent() == satellitesMaxGapPercent
                    && p.satellitesMinGapBlocks() == satellitesMinGapBlocks
                    && p.satellitesMinElevationDegrees() == satellitesMinElevation
                    && p.satellitesMaxElevationDegrees() == satellitesMaxElevation) {
                return layout;
            }
        }

        SphereLayout fresh = new SphereLayout(new Params(spacing, centerY, minRadius, maxRadius,
                satellitesEnabled, satellitesMaxCount,
                satellitesMinSizePercent, satellitesMaxSizePercent,
                satellitesMinGapPercent, satellitesMaxGapPercent, satellitesMinGapBlocks,
                satellitesMinElevation, satellitesMaxElevation));
        current = fresh;
        return fresh;
    }

    /** Valida la entrada de configuracion sin cambiar el hash ni el contrato geometrico legacy. */
    public static void validateConfiguredRadii(int minRadius, int maxRadius) {
        if (minRadius < 1 || minRadius > maxRadius || maxRadius > 46340) {
            throw new IllegalArgumentException("Biospheres requires 1 <= Settings.min_radius <= Settings.radius <= 46340");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public Params params() {
        return params;
    }

    // ------------------------------------------------------------------
    // Celda, centro y radio
    // ------------------------------------------------------------------

    /** Indice de celda que contiene la coordenada {@code x}. */
    public int cellX(double x) {
        return (int) Math.round(x / params.spacing());
    }

    /** Indice de celda que contiene la coordenada {@code z}. */
    public int cellZ(double z) {
        return (int) Math.round(z / params.spacing());
    }

    /** Primera celda que puede tocar un chunk cuyo minimo en el eje es {@code blockCoord}. */
    public int firstCellIndex(int blockCoord) {
        return (int) Math.floor((double) blockCoord / params.spacing());
    }

    /** Ultima celda que puede tocar un chunk cuyo maximo en el eje es {@code blockCoord}. */
    public int lastCellIndex(int blockCoord) {
        return (int) Math.ceil((double) blockCoord / params.spacing());
    }

    /** Centro en X de la celda indicada, conservando la multiplicacion {@code int * int}. */
    public double centerX(int cellX) {
        return cellX * params.spacing();
    }

    /** Centro en Z de la celda indicada, conservando la multiplicacion {@code int * int}. */
    public double centerZ(int cellZ) {
        return cellZ * params.spacing();
    }

    /**
     * Radio de la esfera de una celda. Copia literal del hash anterior:
     * {@code seed * 1664525 + 1013904223} en aritmetica {@code int}.
     */
    public int radiusAt(int cellX, int cellZ) {
        int minRadius = params.minRadius();
        int maxRadius = params.maxRadius();
        if (minRadius >= maxRadius) {
            return maxRadius;
        }
        int seed = cellX * 31 + cellZ;
        int hash = seed * 1664525 + 1013904223;
        return minRadius + Math.abs(hash) % (maxRadius - minRadius + 1);
    }

    /**
     * Esfera de una celda de la cuadricula, identificada por sus indices de celda.
     * Calculada una vez y guardada en la cache.
     *
     * <p>No existe ninguna sobrecarga llamada {@code sphereAt}: el nombre
     * {@code sphereAtCell} recibe indices de celda y {@code sphereContaining} recibe
     * coordenadas de bloque. Distinguirlos por nombre evita que el compilador elija
     * la version equivocada por una conversion implicita de {@code int}.</p>
     */
    public Sphere sphereAtCell(int cellX, int cellZ) {
        int slot = slot(cellX, cellZ);
        Sphere cached = cache.get(slot);
        if (cached != null && cached.cellX() == cellX && cached.cellZ() == cellZ) {
            return cached;
        }
        Sphere fresh = new Sphere(cellX, cellZ, centerX(cellX), params.centerY(), centerZ(cellZ), radiusAt(cellX, cellZ));
        cache.set(slot, fresh);
        return fresh;
    }

    /** Esfera de la celda que contiene la posicion de bloque indicada. */
    public Sphere sphereContaining(double x, double z) {
        return sphereAtCell(cellX(x), cellZ(z));
    }

    // ------------------------------------------------------------------
    // Satelites
    // ------------------------------------------------------------------

    /**
     * Predicado puro de la puerta de los satelites: solo se calculan en un chunk
     * marcado por un generador Biospheres y con la opcion activa. La marca del chunk
     * la pone {@code MixinNoiseBasedChunkGenerator.createNoiseChunk} antes del
     * estado CARVERS; en un generador vanilla nunca se pone.
     */
    public static boolean satellitesAllowed(boolean biosphereChunk, boolean satellitesEnabled) {
        return biosphereChunk && satellitesEnabled;
    }

    /**
     * Satelites de una celda, en orden estable. Devuelve una lista inmutable y
     * vacia cuando los satelites estan apagados, sin tocar ninguna cache.
     *
     * <p>La lista se calcula una sola vez por celda. Una carrera entre dos hilos
     * puede calcularla dos veces, pero ambos resultados son iguales porque la
     * colocacion es una funcion pura de la celda y de los parametros, y la lista
     * que se publica es inmutable.</p>
     */
    public List<Satellite> satellitesAtCell(int cellX, int cellZ) {
        AtomicReferenceArray<SatelliteCell> cache = this.satelliteCache;
        if (cache == null) {
            return List.of();
        }

        int slot = slot(cellX, cellZ);
        SatelliteCell cached = cache.get(slot);
        if (cached != null && cached.cellX() == cellX && cached.cellZ() == cellZ) {
            return cached.satellites();
        }

        Sphere principal = sphereAtCell(cellX, cellZ);
        List<Sphere> neighbours = new ArrayList<>(8);
        for (int gx = cellX - 1; gx <= cellX + 1; gx++) {
            for (int gz = cellZ - 1; gz <= cellZ + 1; gz++) {
                if (gx != cellX || gz != cellZ) {
                    neighbours.add(sphereAtCell(gx, gz));
                }
            }
        }

        // La celda de una esfera es el conjunto de puntos cuyo Math.round los asigna
        // a su indice: centro +- spacing/2.
        double half = params.spacing() / 2.0;
        SatellitePlacement.Bounds bounds = new SatellitePlacement.Bounds(
                principal.centerX() - half, principal.centerX() + half,
                principal.centerZ() - half, principal.centerZ() + half);

        List<Satellite> placed = SatellitePlacement.place(principal, neighbours, params, bounds, true);
        SatelliteCell fresh = new SatelliteCell(cellX, cellZ, List.copyOf(placed));
        cache.set(slot, fresh);

        return fresh.satellites();
    }

    /**
     * {@code true} si la posicion cae dentro del volumen completo de un satelite,
     * cascaron incluido.
     *
     * <p>Solo consulta los satelites de la celda que contiene el punto: la
     * colocacion garantiza que cada satelite cabe entero en su celda, asi que las
     * celdas vecinas no pueden aportar ninguno.</p>
     */
    public boolean isInsideSatellite(double x, double y, double z) {
        if (satelliteCache == null) {
            return false;
        }
        for (Satellite satellite : satellitesAtCell(cellX(x), cellZ(z))) {
            double dx = x - satellite.centerX();
            double dy = y - satellite.centerY();
            double dz = z - satellite.centerZ();
            double radius = satellite.radius();
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Pertenencia y distancia
    // ------------------------------------------------------------------

    /**
     * Esferas candidatas que pueden afectar a una posicion: la celda que la contiene
     * y sus ocho vecinas, en orden fijo (X ascendente, luego Z ascendente). Hoy este
     * es el conjunto que recorre la prueba de pertenencia; esta pensado para que los
     * satelites y el oceano exterior puedan devolver mas de una esfera.
     */
    public List<Sphere> candidatesAt(double x, double y, double z) {
        int gridX = cellX(x);
        int gridZ = cellZ(z);
        List<Sphere> candidates = new ArrayList<>(9);
        for (int gx = gridX - 1; gx <= gridX + 1; gx++) {
            for (int gz = gridZ - 1; gz <= gridZ + 1; gz++) {
                candidates.add(sphereAtCell(gx, gz));
            }
        }
        return candidates;
    }

    /**
     * Esferas candidatas cuyo interior contiene la posicion, aplicando el margen.
     * Devuelve una lista vacia si ninguna la contiene; hoy, con los valores por
     * defecto, solo puede tener cero o un elemento.
     */
    public List<Sphere> spheresAt(double x, double y, double z, double edgeMargin) {
        List<Sphere> candidates = candidatesAt(x, y, z);
        List<Sphere> inside = new ArrayList<>(candidates.size());
        for (Sphere sphere : candidates) {
            if (contains(sphere, x, y, z, edgeMargin)) {
                inside.add(sphere);
            }
        }
        return inside;
    }

    /**
     * {@code true} si la posicion cae dentro de alguna esfera candidata, con el mismo
     * predicado que el codigo anterior: {@code d^2 < (radius - 2 - edgeMargin)^2}.
     *
     * <p>No reserva memoria: recorre las nueve celdas y termina en cuanto encuentra
     * una coincidencia. Es la ruta caliente (biomas, estructuras, puentes).</p>
     *
     * <p>El cuadrado se conserva a proposito. Con {@code edgeMargin > radius - 2} el
     * radio efectivo es negativo y su cuadrado vuelve a ser positivo, de modo que la
     * prueba se comporta como {@code d < |radius - 2 - edgeMargin|}. Eso es un
     * comportamiento existente y no se normaliza.</p>
     */
    public boolean isInside(double x, double y, double z, double edgeMargin) {
        int gridX = cellX(x);
        int gridZ = cellZ(z);
        for (int gx = gridX - 1; gx <= gridX + 1; gx++) {
            for (int gz = gridZ - 1; gz <= gridZ + 1; gz++) {
                if (contains(sphereAtCell(gx, gz), x, y, z, edgeMargin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code true} si la posicion cae dentro de alguna esfera sin aplicar margen. */
    public boolean isInside(double x, double y, double z) {
        return isInside(x, y, z, 0);
    }

    /**
     * Distancia al borde interior del cascaron de la esfera candidata que mas contiene
     * a la posicion: {@code (radius - 2) - distancia al centro}. Positivo dentro del
     * hueco, negativo fuera del cascaron.
     *
     * <p>Esta metrica no existia antes y es nueva; no equivale a {@link #isInside} en
     * el caso de radio efectivo negativo, porque alli el cuadrado invierte el signo.
     * Se toma el maximo sobre las nueve candidatas para que el campo sea continuo.</p>
     */
    public double distanceToEdge(double x, double y, double z) {
        int gridX = cellX(x);
        int gridZ = cellZ(z);
        double best = Double.NEGATIVE_INFINITY;
        for (int gx = gridX - 1; gx <= gridX + 1; gx++) {
            for (int gz = gridZ - 1; gz <= gridZ + 1; gz++) {
                Sphere sphere = sphereAtCell(gx, gz);
                double dx = x - sphere.centerX();
                double dy = y - sphere.centerY();
                double dz = z - sphere.centerZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double toEdge = (sphere.radius() - SHELL_THICKNESS) - distance;
                if (toEdge > best) {
                    best = toEdge;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private boolean contains(Sphere sphere, double x, double y, double z, double edgeMargin) {
        double dx = x - sphere.centerX();
        double dy = y - sphere.centerY();
        double dz = z - sphere.centerZ();
        double effectiveRadius = sphere.radius() - SHELL_THICKNESS - edgeMargin;
        double innerRadiusSq = effectiveRadius * effectiveRadius;
        return dx * dx + dy * dy + dz * dz < innerRadiusSq;
    }

    private static int slot(int cellX, int cellZ) {
        int hash = cellX * 0x9E3779B1 ^ cellZ * 0x85EBCA77;
        hash ^= hash >>> 16;
        return hash & CACHE_MASK;
    }
}
