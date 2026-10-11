package cn.mlus.biosphereworlds.worldgen;

import cn.mlus.biosphereworlds.config.SphereConfig;

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

    /** Tamano de la cache por celda: {@code 1 << CACHE_BITS} entradas. */
    private static final int CACHE_BITS = 12;
    private static final int CACHE_SIZE = 1 << CACHE_BITS;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private static volatile SphereLayout current;

    private final Params params;
    private final AtomicReferenceArray<Sphere> cache = new AtomicReferenceArray<>(CACHE_SIZE);

    public SphereLayout(Params params) {
        this.params = params;
    }

    /** Instantanea de los parametros de la cuadricula. Inmutable. */
    public record Params(int spacing, int centerY, int minRadius, int maxRadius) {
        public Params {
            if (spacing <= 0) {
                throw new IllegalArgumentException("spacing must be > 0, got " + spacing);
            }
        }
    }

    /** Una esfera concreta de la cuadricula. Inmutable. */
    public record Sphere(int cellX, int cellZ, double centerX, double centerZ, int radius) {
    }

    // ------------------------------------------------------------------
    // Construccion desde la configuracion
    // ------------------------------------------------------------------

    /**
     * Devuelve la instancia que refleja la configuracion actual, creandola o
     * reemplazandola solo cuando algun parametro cambia. En el caso normal esto son
     * cuatro lecturas de config y ninguna reserva de memoria.
     */
    public static SphereLayout current() {
        int spacing = SphereConfig.SPACING.get().intValue();
        int centerY = SphereConfig.CENTER_Y.get().intValue();
        int minRadius = SphereConfig.MIN_RADIUS.get().intValue();
        int maxRadius = SphereConfig.RADIUS.get().intValue();

        SphereLayout layout = current;
        if (layout != null) {
            Params p = layout.params;
            if (p.spacing() == spacing && p.centerY() == centerY
                    && p.minRadius() == minRadius && p.maxRadius() == maxRadius) {
                return layout;
            }
        }

        SphereLayout fresh = new SphereLayout(new Params(spacing, centerY, minRadius, maxRadius));
        current = fresh;
        return fresh;
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
        Sphere fresh = new Sphere(cellX, cellZ, centerX(cellX), centerZ(cellZ), radiusAt(cellX, cellZ));
        cache.set(slot, fresh);
        return fresh;
    }

    /** Esfera de la celda que contiene la posicion de bloque indicada. */
    public Sphere sphereContaining(double x, double z) {
        return sphereAtCell(cellX(x), cellZ(z));
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
        double centerY = params.centerY();
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
        double centerY = params.centerY();
        double best = Double.NEGATIVE_INFINITY;
        for (int gx = gridX - 1; gx <= gridX + 1; gx++) {
            for (int gz = gridZ - 1; gz <= gridZ + 1; gz++) {
                Sphere sphere = sphereAtCell(gx, gz);
                double dx = x - sphere.centerX();
                double dy = y - centerY;
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
        double dy = y - params.centerY();
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
