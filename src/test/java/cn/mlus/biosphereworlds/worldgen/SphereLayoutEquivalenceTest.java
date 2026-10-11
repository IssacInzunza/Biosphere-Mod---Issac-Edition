package cn.mlus.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de equivalencia entre {@link SphereLayout} y la geometria anterior.
 *
 * <p>{@link Legacy} es una copia literal, parametrizada, de las formulas que estaban
 * repartidas por el mod antes del refactor. Esta escrito a proposito sin depender de
 * {@code SphereConfig} ni de Minecraft, para que la prueba sea pura y rapida. Se
 * conserva aqui, no en produccion, como oraculo de la equivalencia.</p>
 *
 * <p>La muestra cubre celdas positivas y negativas, bordes de celda (el caso
 * {@code .5} de {@code Math.round}), los dos caminos de {@code minRadius >= maxRadius},
 * margenes negativos ({@code -4}, el del bioma) y margenes que vuelven negativo el
 * radio efectivo. La unica diferencia aceptada es la division en {@code float} de dos
 * sitios, que solo diverge por encima de ~2^24; se comprueba por separado.</p>
 */
class SphereLayoutEquivalenceTest {

    /** Copia literal de la geometria anterior. Solo como oraculo de la prueba. */
    static final class Legacy {

        static int gridX(double x, int spacing) {
            return (int) Math.round(x / spacing);
        }

        static int gridZ(double z, int spacing) {
            return (int) Math.round(z / spacing);
        }

        static int firstCell(int blockCoord, int spacing) {
            return (int) Math.floor((double) blockCoord / spacing);
        }

        static int lastCell(int blockCoord, int spacing) {
            return (int) Math.ceil((double) blockCoord / spacing);
        }

        static double centerX(int gridX, int spacing) {
            return gridX * spacing;
        }

        static double centerZ(int gridZ, int spacing) {
            return gridZ * spacing;
        }

        /** Copia literal de SphereCarver.getSphereRadius. */
        static int radius(int gx, int gz, int minRadius, int maxRadius) {
            if (minRadius >= maxRadius) {
                return maxRadius;
            }
            int seed = gx * 31 + gz;
            int hash = seed * 1664525 + 1013904223;
            return minRadius + Math.abs(hash) % (maxRadius - minRadius + 1);
        }

        /** Copia literal de SphereCarver.isInsideAnySphere, con la vecindad 3x3. */
        static boolean isInsideAnySphere(double x, double y, double z, double edgeMargin,
                                         int spacing, int centerY, int minRadius, int maxRadius) {
            int gx0 = gridX(x, spacing);
            int gz0 = gridZ(z, spacing);
            for (int gx = gx0 - 1; gx <= gx0 + 1; gx++) {
                for (int gz = gz0 - 1; gz <= gz0 + 1; gz++) {
                    double cx = centerX(gx, spacing);
                    double cz = centerZ(gz, spacing);
                    int r = radius(gx, gz, minRadius, maxRadius);
                    double dx = x - cx;
                    double dy = y - centerY;
                    double dz = z - cz;
                    double effectiveRadius = r - 2.0 - edgeMargin;
                    double innerRadiusSq = effectiveRadius * effectiveRadius;
                    if (dx * dx + dy * dy + dz * dz < innerRadiusSq) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Copia literal del predicado de cascaron de SphereCarver.carve. */
        static boolean inShell(double x, double y, double z,
                               int spacing, int centerY, int minRadius, int maxRadius) {
            int gx = gridX(x, spacing);
            int gz = gridZ(z, spacing);
            int r = radius(gx, gz, minRadius, maxRadius);
            double dx = x - centerX(gx, spacing);
            double dy = y - centerY;
            double dz = z - centerZ(gz, spacing);
            double distanceSq = dx * dx + dy * dy + dz * dz;
            double innerRadiusSq = (r - 2.0) * (r - 2.0);
            double outerRadiusSq = r * r;
            return distanceSq >= innerRadiusSq && distanceSq <= outerRadiusSq;
        }

        /** Copia literal de MixinNoiseBasedAquifer.onComputeSubstance. */
        static boolean aquiferOutside(double x, double y, double z,
                                      int spacing, int centerY, int minRadius, int maxRadius) {
            int gx = gridX(x, spacing);
            int gz = gridZ(z, spacing);
            int r = radius(gx, gz, minRadius, maxRadius);
            double dx = x - centerX(gx, spacing);
            double dy = y - centerY;
            double dz = z - centerZ(gz, spacing);
            double distance = dx * dx + dy * dy + dz * dz;
            return distance - r * r >= -r * 2;
        }

        /** Variante en float de SphereUtil/MixinNoiseBasedAquifer, tal como estaba. */
        static boolean aquiferOutsideFloat(double x, double y, double z,
                                           int spacing, int centerY, int minRadius, int maxRadius) {
            int gx = Math.round((float) x / spacing);
            int gz = Math.round((float) z / spacing);
            int r = radius(gx, gz, minRadius, maxRadius);
            double gridX = Math.round((float) x / spacing) * spacing;
            double gridZ = Math.round((float) z / spacing) * spacing;
            double dx = x - gridX;
            double dy = y - centerY;
            double dz = z - gridZ;
            double distance = dx * dx + dy * dy + dz * dz;
            return distance - r * r >= -r * 2;
        }

        /**
         * Copia literal de MultipleSpheresDistanceFunction.compute (D4). Devuelve el
         * valor que alimenta el range_choice de final_density: [0,1) = terreno,
         * fuera de rango = -1000000 (vacio).
         */
        static double multipleSpheresValue(int bx, int by, int bz,
                                           int spacing, int centerY, int minRadius, int maxRadius) {
            double gridX = Math.round((double) bx / spacing) * spacing;
            double gridZ = Math.round((double) bz / spacing) * spacing;
            double dx = bx - gridX;
            double dy = by - centerY;
            double dz = bz - gridZ;
            double distance = dx * dx + dy * dy + dz * dz;
            int r = radius(gridX(bx, spacing), gridZ(bz, spacing), minRadius, maxRadius);
            return distance / (r * r);
        }

        /** Copia literal de MixinNoiseBasedAquifer.onComputeSubstance (D5): true si devuelve AIRE. */
        static boolean aquiferIsAir(int bx, int by, int bz,
                                    int spacing, int centerY, int minRadius, int maxRadius) {
            int r = radius(gridX(bx, spacing), gridZ(bz, spacing), minRadius, maxRadius);
            double gridX = Math.round((float) bx / spacing) * spacing;
            double gridZ = Math.round((float) bz / spacing) * spacing;
            double dx = bx - gridX;
            double dy = by - centerY;
            double dz = bz - gridZ;
            double distance = dx * dx + dy * dy + dz * dz;
            return distance - r * r >= -r * 2;
        }
    }

    private record Config(String name, int spacing, int centerY, int minRadius, int maxRadius) {
    }

    private static final List<Config> CONFIGS = List.of(
            // Valores por defecto del mod: minRadius == maxRadius, el hash no se evalua.
            new Config("default", 500, 62, 128, 128),
            // Rango real de radios: ejercita el hash y el modulo.
            new Config("rango", 500, 62, 64, 128),
            // Rango amplio y centro alto.
            new Config("amplio", 500, 80, 0, 300),
            // minRadius > maxRadius: camino corto.
            new Config("min>max", 500, 62, 200, 100),
            // Cuadricula densa: las esferas vecinas si se tocan.
            new Config("densa", 100, 40, 48, 96)
    );

    private static final double[] MARGINS = {0, -4, -100, 1, 16, 2, 126, 128, 200, 1000};

    private static final int SAMPLES = 20_000;

    @Test
    void celdaYCentroCoincidenConLaLogicaVieja() {
        Random random = new Random(20240101L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                double x = coordinate(random, config.spacing());
                double z = coordinate(random, config.spacing());

                int expectedGx = Legacy.gridX(x, config.spacing());
                int expectedGz = Legacy.gridZ(z, config.spacing());
                assertEquals(expectedGx, layout.cellX(x), () -> config.name() + " cellX(" + x + ")");
                assertEquals(expectedGz, layout.cellZ(z), () -> config.name() + " cellZ(" + z + ")");

                assertEquals(Legacy.centerX(expectedGx, config.spacing()), layout.centerX(expectedGx),
                        () -> config.name() + " centerX(" + expectedGx + ")");
                assertEquals(Legacy.centerZ(expectedGz, config.spacing()), layout.centerZ(expectedGz),
                        () -> config.name() + " centerZ(" + expectedGz + ")");
            }
        }
    }

    /**
     * Bordes exactos: el caso {@code .5} de {@code Math.round} redondea hacia arriba y
     * {@code -0.5} hacia cero. Se comprueba aparte porque es el unico punto donde una
     * reescritura de la celda cambia de celda entera.
     */
    @Test
    void bordesDeCeldaCoincidenConLaLogicaVieja() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            int spacing = config.spacing();
            for (int cell = -80; cell <= 80; cell++) {
                double base = cell * (double) spacing;
                double[] probes = {
                        base,
                        base + spacing / 2.0,
                        base - spacing / 2.0,
                        base + spacing / 2.0 - 0.5,
                        base - spacing / 2.0 + 0.5
                };
                for (double probe : probes) {
                    assertEquals(Legacy.gridX(probe, spacing), layout.cellX(probe),
                            () -> config.name() + " bordes cellX(" + probe + ")");
                    assertEquals(Legacy.gridZ(probe, spacing), layout.cellZ(probe),
                            () -> config.name() + " bordes cellZ(" + probe + ")");
                }
            }
        }
    }

    @Test
    void enumeracionDeChunkCoincideConLaLogicaVieja() {
        Random random = new Random(7L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                int blockCoord = random.nextInt(4_000_000) - 2_000_000;
                assertEquals(Legacy.firstCell(blockCoord, config.spacing()), layout.firstCellIndex(blockCoord),
                        () -> config.name() + " firstCellIndex(" + blockCoord + ")");
                assertEquals(Legacy.lastCell(blockCoord, config.spacing()), layout.lastCellIndex(blockCoord),
                        () -> config.name() + " lastCellIndex(" + blockCoord + ")");
            }
        }
    }

    @Test
    void radioCoincideConLaLogicaVieja() {
        Random random = new Random(99L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                int gx = random.nextInt(20_000) - 10_000;
                int gz = random.nextInt(20_000) - 10_000;
                int expected = Legacy.radius(gx, gz, config.minRadius(), config.maxRadius());
                assertEquals(expected, layout.radiusAt(gx, gz),
                        () -> config.name() + " radiusAt(" + gx + "," + gz + ")");
                assertEquals(expected, layout.sphereAtCell(gx, gz).radius());
            }
        }
    }

    /** Malla densa de celdas: ejercita el desbordamiento de {@code int} del hash. */
    @Test
    void radioCoincideEnUnaMallaDensa() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int gx = -400; gx <= 400; gx++) {
                for (int gz = -30; gz <= 30; gz++) {
                    final int cellX = gx;
                    final int cellZ = gz;
                    assertEquals(Legacy.radius(gx, gz, config.minRadius(), config.maxRadius()),
                            layout.radiusAt(gx, gz),
                            () -> config.name() + " radiusAt(" + cellX + "," + cellZ + ")");
                }
            }
        }
    }

    /**
     * El caso patologico del hash: cuando {@code hash == Integer.MIN_VALUE},
     * {@code Math.abs} lo deja negativo y el radio puede quedar por debajo de
     * {@code minRadius}. No se arregla; se comprueba que el caso se alcanza y que la
     * nueva implementacion da el mismo resultado que la vieja.
     */
    @Test
    void hashPatologicoSigueIgualQueAntes() {
        BigInteger modulus = BigInteger.ONE.shiftLeft(32);
        int target = Integer.MIN_VALUE - 1013904223; // aritmetica int, desborda a proposito
        long seed = BigInteger.valueOf(1664525L)
                .modInverse(modulus)
                .multiply(BigInteger.valueOf(target & 0xFFFFFFFFL))
                .mod(modulus)
                .longValue();
        int seedInt = (int) seed;

        // seed = gx * 31 + gz; elegimos gz en [0, 30] congruente con seed.
        int gz = ((seedInt % 31) + 31) % 31;
        int gx = (seedInt - gz) / 31;

        int hash = gx * 31 * 1664525 + gz * 1664525 + 1013904223;
        assertEquals(Integer.MIN_VALUE, hash, "no se encontro el caso patologico del hash");
        assertEquals(seedInt, gx * 31 + gz, "la descomposicion de la semilla debe ser exacta");

        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            int expected = Legacy.radius(gx, gz, config.minRadius(), config.maxRadius());
            assertEquals(expected, layout.radiusAt(gx, gz),
                    () -> config.name() + " radiusAt patologico(" + gx + "," + gz + ")");
        }
    }

    @Test
    void pertenenciaCoincideConLaLogicaVieja() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            Random random = new Random(4242L);
            for (int i = 0; i < SAMPLES; i++) {
                double x = coordinate(random, config.spacing());
                double z = coordinate(random, config.spacing());
                double y = random.nextInt(400) - 64;
                for (double margin : MARGINS) {
                    boolean expected = Legacy.isInsideAnySphere(x, y, z, margin, config.spacing(),
                            config.centerY(), config.minRadius(), config.maxRadius());
                    assertEquals(expected, layout.isInside(x, y, z, margin),
                            () -> config.name() + " isInside(" + x + "," + y + "," + z + ", margin=" + margin + ")");
                }
            }
        }
    }

    /** Muestras concentradas en el borde del cascaron, donde es facil equivocarse. */
    @Test
    void pertenenciaEnElBordeCoincideConLaLogicaVieja() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            int spacing = config.spacing();
            for (int cell = -6; cell <= 6; cell++) {
                for (int cellZ = -6; cellZ <= 6; cellZ++) {
                    int radius = Legacy.radius(cell, cellZ, config.minRadius(), config.maxRadius());
                    double cx = Legacy.centerX(cell, spacing);
                    double cz = Legacy.centerZ(cellZ, spacing);
                    for (int offset = radius - 4; offset <= radius + 4; offset++) {
                        for (double y : new double[]{config.centerY(), config.centerY() + 1, config.centerY() - 1,
                                config.centerY() + radius, config.centerY() - radius}) {
                            double x = cx + offset;
                            double z = cz;
                            for (double margin : new double[]{0, -4, 2, 16}) {
                                assertEquals(
                                        Legacy.isInsideAnySphere(x, y, z, margin, spacing, config.centerY(),
                                                config.minRadius(), config.maxRadius()),
                                        layout.isInside(x, y, z, margin),
                                        () -> config.name() + " borde isInside(" + x + "," + y + "," + z
                                                + ", margin=" + margin + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void cascaronCoincideConLaLogicaVieja() {
        Random random = new Random(5150L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                double x = coordinate(random, config.spacing());
                double z = coordinate(random, config.spacing());
                double y = random.nextInt(400) - 64;
                SphereLayout.Sphere sphere = layout.sphereContaining(x, z);
                double dx = x - sphere.centerX();
                double dy = y - layout.params().centerY();
                double dz = z - sphere.centerZ();
                double distanceSq = dx * dx + dy * dy + dz * dz;
                double innerRadiusSq = (sphere.radius() - SphereLayout.SHELL_THICKNESS)
                        * (sphere.radius() - SphereLayout.SHELL_THICKNESS);
                double outerRadiusSq = (double) sphere.radius() * sphere.radius();
                boolean actual = distanceSq >= innerRadiusSq && distanceSq <= outerRadiusSq;

                assertEquals(Legacy.inShell(x, y, z, config.spacing(), config.centerY(),
                                config.minRadius(), config.maxRadius()), actual,
                        () -> config.name() + " shell(" + x + "," + y + "," + z + ")");
            }
        }
    }

    @Test
    void acuiferoCoincideConLaLogicaVieja() {
        Random random = new Random(31337L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                double x = coordinate(random, config.spacing());
                double z = coordinate(random, config.spacing());
                double y = random.nextInt(400) - 64;
                SphereLayout.Sphere sphere = layout.sphereContaining(x, z);
                double dx = x - sphere.centerX();
                double dy = y - layout.params().centerY();
                double dz = z - sphere.centerZ();
                double distance = dx * dx + dy * dy + dz * dz;
                boolean actual = distance - sphere.radius() * sphere.radius() >= -sphere.radius() * 2;

                assertEquals(Legacy.aquiferOutside(x, y, z, config.spacing(), config.centerY(),
                                config.minRadius(), config.maxRadius()), actual,
                        () -> config.name() + " aquifer(" + x + "," + y + "," + z + ")");
            }
        }
    }

    /**
     * La unica divergencia aceptada: dos sitios dividian en {@code float}. La eleccion de
     * {@code double} da el mismo resultado mientras la coordenada no se acerque al limite
     * de precision de {@code float}; se comprueba lejos de los bordes de celda, que son
     * los unicos puntos donde el redondeo podria cambiar de celda.
     */
    @Test
    void varianteFloatCoincideEnElRangoNormal() {
        Random random = new Random(2718L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            int spacing = config.spacing();
            for (int i = 0; i < SAMPLES; i++) {
                double x = (random.nextDouble() * 2 - 1) * (1 << 20);
                double z = (random.nextDouble() * 2 - 1) * (1 << 20);
                double y = random.nextInt(400) - 64;

                // Fuera de la zona donde el error de float podria cruzar un borde de celda.
                if (farFromCellBoundary(x, spacing) && farFromCellBoundary(z, spacing)) {
                    assertEquals(Legacy.gridX(x, spacing), Math.round((float) x / spacing),
                            () -> config.name() + " celda float vs double en x=" + x);
                    assertEquals(Legacy.aquiferOutsideFloat(x, y, z, spacing, config.centerY(),
                                    config.minRadius(), config.maxRadius()),
                            Legacy.aquiferOutside(x, y, z, spacing, config.centerY(),
                                    config.minRadius(), config.maxRadius()),
                            () -> config.name() + " acuifero float vs double en (" + x + "," + y + "," + z + ")");
                }

                // La implementacion nueva siempre debe coincidir con la variante double.
                assertEquals(Legacy.aquiferOutside(x, y, z, spacing, config.centerY(),
                                config.minRadius(), config.maxRadius()),
                        aquiferOutsideOf(layout, x, y, z),
                        () -> config.name() + " acuifero(" + x + "," + y + "," + z + ")");
            }
        }
    }

    @Test
    void listaDeCandidatasTieneOrdenFijo() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            SphereLayout.Sphere origin = layout.sphereAtCell(3, -2);
            List<SphereLayout.Sphere> candidates =
                    layout.candidatesAt(origin.centerX(), config.centerY(), origin.centerZ());
            assertEquals(9, candidates.size());
            for (int i = 0; i < 9; i++) {
                int expectedX = 3 - 1 + i / 3;
                int expectedZ = -2 - 1 + i % 3;
                assertEquals(expectedX, candidates.get(i).cellX(), "orden en X; indice " + i);
                assertEquals(expectedZ, candidates.get(i).cellZ(), "orden en Z; indice " + i);
            }
        }
    }

    /**
     * La lista de esferas que contienen una posicion debe ser coherente con la prueba
     * puntual: vacia si y solo si {@code isInside} es falso, y siempre un subconjunto de
     * las candidatas.
     */
    @Test
    void listaDeEsferasContenidasEsCoherenteConIsInside() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            Random random = new Random(1234L);
            for (int i = 0; i < SAMPLES / 4; i++) {
                double x = coordinate(random, config.spacing());
                double z = coordinate(random, config.spacing());
                double y = random.nextInt(400) - 64;
                List<SphereLayout.Sphere> candidates = layout.candidatesAt(x, y, z);
                for (double margin : MARGINS) {
                    List<SphereLayout.Sphere> inside = layout.spheresAt(x, y, z, margin);
                    boolean expected = Legacy.isInsideAnySphere(x, y, z, margin, config.spacing(),
                            config.centerY(), config.minRadius(), config.maxRadius());
                    assertEquals(expected, layout.isInside(x, y, z, margin));
                    assertEquals(expected, !inside.isEmpty(),
                            () -> config.name() + " spheresAt discrepa de isInside en (" + x + "," + y + "," + z
                                    + ", margin=" + margin + ")");
                    assertTrue(candidates.containsAll(inside),
                            () -> config.name() + " spheresAt devolvio una esfera no candidata");
                }
            }
        }
    }

    @Test
    void distanceToEdgeEsConsistenteConElCascaron() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            SphereLayout.Sphere sphere = layout.sphereAtCell(0, 0);
            double centerY = layout.params().centerY();

            // En el centro exacto la distancia es el radio interior.
            assertEquals(sphere.radius() - SphereLayout.SHELL_THICKNESS,
                    layout.distanceToEdge(sphere.centerX(), centerY, sphere.centerZ()), 1.0e-9);

            // Al alejarse 10 bloques en X decrece exactamente 10.
            assertEquals(layout.distanceToEdge(sphere.centerX(), centerY, sphere.centerZ()) - 10.0,
                    layout.distanceToEdge(sphere.centerX() + 10, centerY, sphere.centerZ()), 1.0e-9);
        }
    }

    @Test
    void esInmutableYDeterministaEntreHilos() throws InterruptedException {
        final Config config = CONFIGS.get(1);
        final SphereLayout layout = new SphereLayout(params(config));
        final int threads = 8;
        final int iterations = 10_000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    Random random = new Random(seed);
                    for (int i = 0; i < iterations; i++) {
                        double x = coordinate(random, config.spacing());
                        double z = coordinate(random, config.spacing());
                        double y = random.nextInt(400) - 64;
                        double margin = MARGINS[random.nextInt(MARGINS.length)];
                        if (layout.isInside(x, y, z, margin)
                                != Legacy.isInsideAnySphere(x, y, z, margin, config.spacing(),
                                config.centerY(), config.minRadius(), config.maxRadius())) {
                            throw new AssertionError("resultado inestable en (" + x + "," + y + "," + z + ")");
                        }
                        if (layout.sphereContaining(x, z).radius()
                                != Legacy.radius(Legacy.gridX(x, config.spacing()),
                                Legacy.gridZ(z, config.spacing()), config.minRadius(), config.maxRadius())) {
                            throw new AssertionError("radio inestable en (" + x + "," + z + ")");
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "los hilos no terminaron a tiempo");
        assertNull(failure.get(), () -> "fallo concurrente: " + failure.get());
    }

    /**
     * D4: la funcion de densidad debe dar el mismo valor que antes. Espeja la llamada
     * de produccion, que pasa coordenadas de bloque {@code int}.
     */
    @Test
    void d4FuncionDeDensidadCoincideConLaLogicaVieja() {
        Random random = new Random(8080L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                int bx = (int) coordinate(random, config.spacing());
                int bz = (int) coordinate(random, config.spacing());
                int by = random.nextInt(400) - 64;
                double expected = Legacy.multipleSpheresValue(bx, by, bz, config.spacing(),
                        config.centerY(), config.minRadius(), config.maxRadius());
                double actual = newMultipleSpheresValue(layout, bx, by, bz);
                assertEquals(expected, actual, 1.0e-9,
                        () -> config.name() + " D4 en bloque (" + bx + "," + by + "," + bz + ")");
            }
        }
    }

    /**
     * D4 en el centro exacto de cada esfera: el valor debe caer en {@code [0,1)} para
     * que {@code range_choice} elija la rama de terreno. Un valor &ge; 1 significa
     * {@code -1000000} y por tanto vacio: cascaron sin terreno.
     */
    @Test
    void d4DaValorDeTerrenoEnElCentroDeCadaEsfera() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int cell = -4; cell <= 4; cell++) {
                for (int cellZ = -4; cellZ <= 4; cellZ++) {
                    final int cellXFinal = cell;
                    final int cellZFinal = cellZ;
                    int bx = (int) Math.round(layout.centerX(cell));
                    int bz = (int) Math.round(layout.centerZ(cellZ));
                    double value = newMultipleSpheresValue(layout, bx, config.centerY(), bz);
                    assertTrue(value >= 0.0 && value < 1.0,
                            () -> config.name() + " D4 fuera de [0,1) en el centro de la celda ("
                                    + cellXFinal + "," + cellZFinal + ") bloque=(" + bx + "," + bz
                                    + ") valor=" + value);
                }
            }
        }
    }

    /**
     * D5: el acuifero debe devolver el mismo resultado que antes. Espeja la llamada de
     * produccion, que pasa coordenadas de bloque {@code int}.
     */
    @Test
    void d5AcuiferoCoincideConLaLogicaVieja() {
        Random random = new Random(9090L);
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            for (int i = 0; i < SAMPLES; i++) {
                int bx = (int) coordinate(random, config.spacing());
                int bz = (int) coordinate(random, config.spacing());
                int by = random.nextInt(400) - 64;
                boolean expected = Legacy.aquiferIsAir(bx, by, bz, config.spacing(),
                        config.centerY(), config.minRadius(), config.maxRadius());
                boolean actual = newAquiferIsAir(layout, bx, by, bz);
                assertEquals(expected, actual,
                        () -> config.name() + " D5 en bloque (" + bx + "," + by + "," + bz + ")");
            }
        }
    }

    /**
     * Fija la semantica de los dos nombres: {@code sphereAtCell} recibe indices de celda
     * y {@code sphereContaining} recibe coordenadas de bloque. Que no exista ninguna
     * sobrecarga llamada {@code sphereAt} es justo lo que impide que vuelva a colarse el
     * bindeo equivocado por conversion implicita de {@code int}.
     */
    @Test
    void sphereContainingDeUnaPosicionUsaLaCeldaQueLaContiene() {
        for (Config config : CONFIGS) {
            SphereLayout layout = new SphereLayout(params(config));
            int bx = config.spacing() * 3;
            int bz = config.spacing() * -2;
            SphereLayout.Sphere viaCeldas = layout.sphereAtCell(layout.cellX(bx), layout.cellZ(bz));
            SphereLayout.Sphere viaBloqueDouble = layout.sphereContaining((double) bx, (double) bz);
            SphereLayout.Sphere viaBloqueInt = layout.sphereContaining(bx, bz);

            assertEquals(viaCeldas.cellX(), viaBloqueDouble.cellX(),
                    () -> config.name() + " sphereContaining(double,double) no usa la celda que contiene el bloque");
            assertEquals(viaCeldas.cellX(), viaBloqueInt.cellX(),
                    () -> config.name() + " sphereContaining con el bloque " + bx
                            + " no uso la celda que lo contiene (devuelve celda " + viaBloqueInt.cellX()
                            + " en vez de " + viaCeldas.cellX() + ")");
            // Y la version de celda no debe confundirse con la de bloque.
            assertEquals(bx, layout.sphereAtCell(bx, bz).cellX(),
                    () -> config.name() + " sphereAtCell debe tratar el argumento como indice de celda");
        }
    }

    private static double newMultipleSpheresValue(SphereLayout layout, int bx, int by, int bz) {
        SphereLayout.Sphere sphere = layout.sphereContaining(bx, bz);
        double dx = bx - sphere.centerX();
        double dy = by - layout.params().centerY();
        double dz = bz - sphere.centerZ();
        double distance = dx * dx + dy * dy + dz * dz;
        int radius = sphere.radius();
        return distance / (radius * radius);
    }

    private static boolean newAquiferIsAir(SphereLayout layout, int bx, int by, int bz) {
        SphereLayout.Sphere sphere = layout.sphereContaining(bx, bz);
        double dx = bx - sphere.centerX();
        double dy = by - layout.params().centerY();
        double dz = bz - sphere.centerZ();
        double distance = dx * dx + dy * dy + dz * dz;
        return distance - sphere.radius() * sphere.radius() >= -sphere.radius() * 2;
    }

    private static boolean aquiferOutsideOf(SphereLayout layout, double x, double y, double z) {
        SphereLayout.Sphere sphere = layout.sphereContaining(x, z);
        double dx = x - sphere.centerX();
        double dy = y - layout.params().centerY();
        double dz = z - sphere.centerZ();
        double distance = dx * dx + dy * dy + dz * dz;
        return distance - sphere.radius() * sphere.radius() >= -sphere.radius() * 2;
    }

    /** {@code true} si el cociente esta lejos de un medio entero, con holgura para float. */
    private static boolean farFromCellBoundary(double value, int spacing) {
        double quotient = value / spacing;
        double fraction = quotient - Math.floor(quotient);
        return Math.abs(fraction - 0.5) > 1.0e-2;
    }

    private static SphereLayout.Params params(Config config) {
        return new SphereLayout.Params(config.spacing(), config.centerY(), config.minRadius(), config.maxRadius());
    }

    /** Coordenada alrededor de la cuadricula, con muchos casos cerca de centros y bordes. */
    private static double coordinate(Random random, int spacing) {
        int mode = random.nextInt(4);
        return switch (mode) {
            case 0 -> random.nextInt(2 * spacing * 20) - spacing * 20;
            case 1 -> random.nextInt(2 * spacing) - spacing;
            case 2 -> random.nextLong() % 40_000_000L;
            default -> random.nextDouble() * 2 * spacing - spacing;
        };
    }
}
