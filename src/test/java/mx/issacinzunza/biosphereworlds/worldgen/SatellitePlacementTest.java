package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la colocacion de satelites. Son puras: no necesitan Minecraft ni la
 * configuracion de NeoForge.
 */
class SatellitePlacementTest {

    private static final int SPACING = 500;
    private static final int CENTER_Y = 62;
    private static final int RADIUS = 128;
    private static final int MAX_COUNT = 3;

    private static SphereLayout.Params enabled() {
        return enabled(MAX_COUNT);
    }

    private static SphereLayout.Params enabled(int maxCount) {
        return new SphereLayout.Params(SPACING, CENTER_Y, RADIUS, RADIUS,
                true, maxCount, 8, 12, 25, 75, 16, 30, 55);
    }

    private static SphereLayout layout(SphereLayout.Params params) {
        return new SphereLayout(params);
    }

    @Test
    void colocaComoMaximoElNumeroConfigurado() {
        SphereLayout layout = layout(enabled());
        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                List<SphereLayout.Satellite> satellites = layout.satellitesAtCell(cellX, cellZ);
                assertTrue(satellites.size() <= MAX_COUNT,
                        "demasiados satelites en (" + cellX + "," + cellZ + "): " + satellites.size());
                for (SphereLayout.Satellite satellite : satellites) {
                    assertTrue(satellite.radius() > 0, "radio no positivo");
                }
            }
        }
    }

    @Test
    void esDeterministaEntreInstanciasYEntreLlamadas() {
        SphereLayout a = layout(enabled());
        SphereLayout b = layout(enabled());
        for (int cellX = -12; cellX <= 12; cellX++) {
            for (int cellZ = -12; cellZ <= 12; cellZ++) {
                List<SphereLayout.Satellite> first = a.satellitesAtCell(cellX, cellZ);
                List<SphereLayout.Satellite> repeated = a.satellitesAtCell(cellX, cellZ);
                List<SphereLayout.Satellite> other = b.satellitesAtCell(cellX, cellZ);
                assertEquals(first, repeated, "la cache no debe cambiar el resultado");
                assertEquals(first, other, "dos instancias con los mismos parametros deben coincidir");
            }
        }
    }

    @Test
    void cadaSateliteCabeEnteroEnLaCeldaConMargen() {
        SphereLayout layout = layout(enabled());
        double half = SPACING / 2.0;
        int margin = 16;
        for (int cellX = -30; cellX <= 30; cellX++) {
            for (int cellZ = -30; cellZ <= 30; cellZ++) {
                double minX = layout.centerX(cellX) - half;
                double maxX = layout.centerX(cellX) + half;
                double minZ = layout.centerZ(cellZ) - half;
                double maxZ = layout.centerZ(cellZ) + half;
                for (SphereLayout.Satellite satellite : layout.satellitesAtCell(cellX, cellZ)) {
                    double r = satellite.radius();
                    assertTrue(satellite.centerX() - r >= minX + margin,
                            "satelite fuera de la celda por -X en (" + cellX + "," + cellZ + ")");
                    assertTrue(satellite.centerX() + r <= maxX - margin,
                            "satelite fuera de la celda por +X en (" + cellX + "," + cellZ + ")");
                    assertTrue(satellite.centerZ() - r >= minZ + margin,
                            "satelite fuera de la celda por -Z en (" + cellX + "," + cellZ + ")");
                    assertTrue(satellite.centerZ() + r <= maxZ - margin,
                            "satelite fuera de la celda por +Z en (" + cellX + "," + cellZ + ")");
                }
            }
        }
    }

    @Test
    void losSatelitesRespetanLosLimitesVerticales() {
        SphereLayout layout = layout(enabled());
        for (int cellX = -30; cellX <= 30; cellX++) {
            for (int cellZ = -30; cellZ <= 30; cellZ++) {
                for (SphereLayout.Satellite satellite : layout.satellitesAtCell(cellX, cellZ)) {
                    double r = satellite.radius();
                    assertTrue(satellite.centerY() - r >= SatellitePlacement.WORLD_MIN_Y,
                            "satelite por debajo de -64 en (" + cellX + "," + cellZ + ")");
                    assertTrue(satellite.centerY() + r <= SatellitePlacement.WORLD_MAX_Y,
                            "satelite por encima de 319 en (" + cellX + "," + cellZ + ")");
                }
            }
        }
    }

    @Test
    void noHaySolapesConLaPrincipalNiEntreSatelites() {
        SphereLayout layout = layout(enabled());
        int margin = 16;
        for (int cellX = -25; cellX <= 25; cellX++) {
            for (int cellZ = -25; cellZ <= 25; cellZ++) {
                SphereLayout.Sphere principal = layout.sphereAtCell(cellX, cellZ);
                List<SphereLayout.Satellite> satellites = layout.satellitesAtCell(cellX, cellZ);
                for (int i = 0; i < satellites.size(); i++) {
                    SphereLayout.Satellite satellite = satellites.get(i);
                    double toPrincipal = distance(satellite.centerX(), satellite.centerY(), satellite.centerZ(),
                            principal.centerX(), principal.centerY(), principal.centerZ());
                    assertTrue(toPrincipal >= principal.radius() + satellite.radius() + margin - 1.0e-9,
                            "solape con la principal en (" + cellX + "," + cellZ + ")");
                    for (int j = i + 1; j < satellites.size(); j++) {
                        SphereLayout.Satellite other = satellites.get(j);
                        double between = distance(satellite.centerX(), satellite.centerY(), satellite.centerZ(),
                                other.centerX(), other.centerY(), other.centerZ());
                        assertTrue(between >= satellite.radius() + other.radius() + margin - 1.0e-9,
                                "solape entre satelites en (" + cellX + "," + cellZ + ")");
                    }
                }
            }
        }
    }

    @Test
    void isInsideSatelliteCubreElVolumenYNoLaCeldaVecina() {
        SphereLayout layout = layout(enabled());
        boolean checked = false;
        for (int cellX = -10; cellX <= 10 && !checked; cellX++) {
            for (int cellZ = -10; cellZ <= 10 && !checked; cellZ++) {
                List<SphereLayout.Satellite> satellites = layout.satellitesAtCell(cellX, cellZ);
                if (satellites.isEmpty()) {
                    continue;
                }
                SphereLayout.Satellite satellite = satellites.get(0);
                assertTrue(layout.isInsideSatellite(satellite.centerX(), satellite.centerY(), satellite.centerZ()),
                        "el centro del satelite debe estar dentro");
                assertFalse(layout.isInsideSatellite(satellite.centerX(),
                                satellite.centerY() + satellite.radius() + 1.0, satellite.centerZ()),
                        "justo encima del volumen no debe estar dentro");
                checked = true;
            }
        }
        assertTrue(checked, "no se encontro ningun satelite en la muestra");
    }

    @Test
    void siNadaCabeSeOmiteSinError() {
        // Celda de 120 bloques: el satelite minimo (8) mas el hueco (25) no cabe
        // con el margen de 16, asi que todos los intentos se rechazan.
        SphereLayout.Params params = new SphereLayout.Params(120, CENTER_Y, 100, 100,
                true, 5, 8, 12, 25, 75, 16, 30, 55);
        SphereLayout layout = layout(params);
        assertDoesNotThrow(() -> {
            for (int cellX = -5; cellX <= 5; cellX++) {
                for (int cellZ = -5; cellZ <= 5; cellZ++) {
                    assertTrue(layout.satellitesAtCell(cellX, cellZ).isEmpty(),
                            "no deberia caber ningun satelite en una celda tan estrecha");
                }
            }
        });
    }

    @Test
    void losSatelitesNoQuedanTodosEnLaMismaDireccion() {
        SphereLayout layout = layout(enabled());
        boolean found = false;
        for (int cellX = -40; cellX <= 40 && !found; cellX++) {
            for (int cellZ = -40; cellZ <= 40 && !found; cellZ++) {
                List<SphereLayout.Satellite> satellites = layout.satellitesAtCell(cellX, cellZ);
                if (satellites.size() < 2) {
                    continue;
                }
                SphereLayout.Sphere principal = layout.sphereAtCell(cellX, cellZ);
                List<Double> angles = new ArrayList<>();
                for (SphereLayout.Satellite satellite : satellites) {
                    angles.add(Math.atan2(satellite.centerZ() - principal.centerZ(),
                            satellite.centerX() - principal.centerX()));
                }
                for (int i = 0; i < angles.size(); i++) {
                    for (int j = i + 1; j < angles.size(); j++) {
                        double difference = Math.abs(angles.get(i) - angles.get(j));
                        difference = Math.min(difference, Math.PI * 2.0 - difference);
                        // El paso aureo separa los indices en ~2.4 rad y el jitter es
                        // como mucho 0.25 rad por satelite.
                        assertTrue(difference > 1.0,
                                "dos satelites casi en la misma direccion en ("
                                        + cellX + "," + cellZ + "): " + difference);
                        found = true;
                    }
                }
            }
        }
        assertTrue(found, "no se encontro una celda con dos o mas satelites");
    }

    @Test
    void conSatellitesApagadosNoHaySatelitesNiCambiaLaGeometria() {
        SphereLayout.Params off = new SphereLayout.Params(SPACING, CENTER_Y, RADIUS, RADIUS);
        assertFalse(off.satellitesEnabled());
        SphereLayout layout = layout(off);
        Random random = new Random(20240202L);
        for (int i = 0; i < 20_000; i++) {
            double x = (random.nextDouble() * 2.0 - 1.0) * 4000.0;
            double z = (random.nextDouble() * 2.0 - 1.0) * 4000.0;
            double y = random.nextInt(400) - 64;
            for (double margin : new double[]{0.0, -4.0, 16.0}) {
                assertEquals(legacyInside(x, y, z, margin), layout.isInside(x, y, z, margin),
                        () -> "isInside cambio con los satelites apagados en ("
                                + x + "," + y + "," + z + ", margin=" + margin + ")");
            }
            assertFalse(layout.isInsideSatellite(x, y, z), "sin satelites nunca hay interior de satelite");
        }
        for (int cellX = -10; cellX <= 10; cellX++) {
            for (int cellZ = -10; cellZ <= 10; cellZ++) {
                assertTrue(layout.satellitesAtCell(cellX, cellZ).isEmpty());
            }
        }
    }

    @Test
    void elParDeTamanosPorDefectoEsValido() {
        SphereLayout.Params params = enabled();
        assertEquals(8, params.satellitesMinSizePercent());
        assertEquals(12, params.satellitesMaxSizePercent());
        assertTrue(params.satellitesMinSizePercent() <= params.satellitesMaxSizePercent());
        assertDoesNotThrow(() -> layout(params).satellitesAtCell(0, 0));
    }

    @Test
    void losRangosInvertidosSeNormalizanSinError() {
        SphereLayout.Params params = new SphereLayout.Params(SPACING, CENTER_Y, RADIUS, RADIUS,
                true, 2, 40, 20, 90, 10, 16, 80, 20);
        assertEquals(20, params.satellitesMinSizePercent());
        assertEquals(10, params.satellitesMinGapPercent());
        assertEquals(20, params.satellitesMinElevationDegrees());
        SphereLayout layout = layout(params);
        assertDoesNotThrow(() -> layout.satellitesAtCell(0, 0));
    }

    @Test
    void laEnumeracionDeChunksIncluyeLaCeldaDueniaDeCadaSatelite() {
        SphereLayout layout = layout(enabled());
        int checked = 0;
        for (int cellX = -30; cellX <= 30; cellX++) {
            for (int cellZ = -30; cellZ <= 30; cellZ++) {
                for (SphereLayout.Satellite satellite : layout.satellitesAtCell(cellX, cellZ)) {
                    int minChunkX = Math.floorDiv((int) Math.floor(satellite.centerX() - satellite.radius()), 16);
                    int maxChunkX = Math.floorDiv((int) Math.ceil(satellite.centerX() + satellite.radius()), 16);
                    int minChunkZ = Math.floorDiv((int) Math.floor(satellite.centerZ() - satellite.radius()), 16);
                    int maxChunkZ = Math.floorDiv((int) Math.ceil(satellite.centerZ() + satellite.radius()), 16);
                    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                            int startX = chunkX * 16;
                            int endX = startX + 15;
                            int startZ = chunkZ * 16;
                            int endZ = startZ + 15;
                            assertTrue(layout.firstCellIndex(startX) <= cellX
                                            && cellX <= layout.lastCellIndex(endX),
                                    "el chunk (" + chunkX + "," + chunkZ
                                            + ") no enumera la celda " + cellX + " del satelite");
                            assertTrue(layout.firstCellIndex(startZ) <= cellZ
                                            && cellZ <= layout.lastCellIndex(endZ),
                                    "el chunk (" + chunkX + "," + chunkZ
                                            + ") no enumera la celda " + cellZ + " del satelite");
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 0, "no se reviso ningun chunk");
    }

    @Test
    void elAccesoConcurrenteDevuelveElMismoResultado() throws InterruptedException {
        final SphereLayout layout = layout(enabled());
        final Map<Long, List<SphereLayout.Satellite>> expected = new HashMap<>();
        for (int cellX = -8; cellX <= 8; cellX++) {
            for (int cellZ = -8; cellZ <= 8; cellZ++) {
                expected.put(key(cellX, cellZ), layout.satellitesAtCell(cellX, cellZ));
            }
        }

        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    Random random = new Random(seed);
                    for (int i = 0; i < 20_000; i++) {
                        int cellX = random.nextInt(17) - 8;
                        int cellZ = random.nextInt(17) - 8;
                        List<SphereLayout.Satellite> actual = layout.satellitesAtCell(cellX, cellZ);
                        if (!expected.get(key(cellX, cellZ)).equals(actual)) {
                            throw new AssertionError("resultado inestable en (" + cellX + "," + cellZ + ")");
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

    /** Copia literal del predicado anterior de pertenencia (solo principales). */
    private static boolean legacyInside(double x, double y, double z, double margin) {
        int gx0 = (int) Math.round(x / SPACING);
        int gz0 = (int) Math.round(z / SPACING);
        for (int gx = gx0 - 1; gx <= gx0 + 1; gx++) {
            for (int gz = gz0 - 1; gz <= gz0 + 1; gz++) {
                double cx = gx * SPACING;
                double cz = gz * SPACING;
                int radius = RADIUS;
                double dx = x - cx;
                double dy = y - CENTER_Y;
                double dz = z - cz;
                double effectiveRadius = radius - 2.0 - margin;
                double innerRadiusSq = effectiveRadius * effectiveRadius;
                if (dx * dx + dy * dy + dz * dz < innerRadiusSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distance(double x1, double y1, double z1,
                                   double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }
}
