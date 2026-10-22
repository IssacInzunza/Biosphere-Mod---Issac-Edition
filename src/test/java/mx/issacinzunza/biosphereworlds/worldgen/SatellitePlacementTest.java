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
        assertConcurrentPlacement(enabled());
        assertConcurrentPlacement(new SphereLayout.Params(4000, CENTER_Y, 256, 512,
                true, MAX_COUNT, 8, 12, 25, 75, 16, 30, 55));
    }

    private static void assertConcurrentPlacement(SphereLayout.Params params) throws InterruptedException {
        final SphereLayout layout = layout(params);
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

    @Test
    void classicLargeCellRecoversRequestedMoonWithoutChangingPrincipal() {
        var params = new SphereLayout.Params(4000, CENTER_Y, 256, 512,
                true, MAX_COUNT, 8, 12, 25, 75, 16, 30, 55);
        var layout = layout(params);
        var principal = layout.sphereAtCell(0, 0);
        assertEquals(415, principal.radius());
        assertEquals(62, principal.centerY());
        assertEquals(1, LegacySatellitePlacement.countAt(0, 0, MAX_COUNT));
        assertTrue(LegacySatellitePlacement.place(principal, List.of(), params,
                new LegacySatellitePlacement.Bounds(-2000, 2000, -2000, 2000)).isEmpty());
        var moons = layout.satellitesAtCell(0, 0);
        assertEquals(1, moons.size());
        assertTrue(layout.isInsideSatellite(moons.getFirst().centerX(), moons.getFirst().centerY(), moons.getFirst().centerZ()));
        assertEquals(principal, layout.sphereAtCell(0, 0));
        // Height adaptation cannot recover a moon when the cell itself is too small.
        var tight = layout(new SphereLayout.Params(500, CENTER_Y, 256, 512,
                true, MAX_COUNT, 8, 12, 25, 75, 16, 30, 55));
        assertTrue(tight.satellitesAtCell(0, 0).isEmpty());
    }

    @Test
    void classicLargeMoonsRespectCellsNeighboursAndSeededZeroCounts() {
        int placed = 0;
        int zeroCounts = 0;
        for (int spacing : new int[]{500, 2000, 4000}) {
            var params = new SphereLayout.Params(spacing, CENTER_Y, 256, 512,
                    true, MAX_COUNT, 8, 12, 25, 75, 16, 30, 55);
            var a = layout(params);
            var b = layout(params);
            // More cells than cache slots also exercises replacement and recomputation.
            for (int cellX = -35; cellX <= 35; cellX++) {
                for (int cellZ = -35; cellZ <= 35; cellZ++) {
                    var moons = a.satellitesAtCell(cellX, cellZ);
                    var parent = a.sphereAtCell(cellX, cellZ);
                    int requested = LegacySatellitePlacement.countAt(cellX, cellZ, MAX_COUNT);
                    assertTrue(moons.size() <= requested);
                    if (requested == 0) {
                        zeroCounts++;
                        assertTrue(moons.isEmpty());
                    }
                    assertEquals(moons, b.satellitesAtCell(cellX, cellZ));
                    for (int index = 0; index < moons.size(); index++) {
                        placed++;
                        var moon = moons.get(index);
                        double r = moon.radius();
                        double half = spacing / 2.0;
                        assertTrue(moon.centerY() - r >= -64);
                        assertTrue(moon.centerY() + r <= 319);
                        assertTrue(moon.centerX() - r >= parent.centerX() - half + 16);
                        assertTrue(moon.centerX() + r <= parent.centerX() + half - 16);
                        assertTrue(moon.centerZ() - r >= parent.centerZ() - half + 16);
                        assertTrue(moon.centerZ() + r <= parent.centerZ() + half - 16);
                        double gap = distance(moon.centerX(), moon.centerY(), moon.centerZ(),
                                parent.centerX(), parent.centerY(), parent.centerZ()) - parent.radius() - r;
                        assertTrue(gap >= parent.radius() * .25 - 1e-8);
                        assertTrue(gap <= parent.radius() * .75 + 1e-8);
                        for (int nx = cellX - 1; nx <= cellX + 1; nx++) {
                            for (int nz = cellZ - 1; nz <= cellZ + 1; nz++) {
                                var neighbour = a.sphereAtCell(nx, nz);
                                assertTrue(distance(moon.centerX(), moon.centerY(), moon.centerZ(),
                                        neighbour.centerX(), neighbour.centerY(), neighbour.centerZ())
                                        >= r + neighbour.radius() + 16 - 1e-8);
                            }
                        }
                        for (int j = 0; j < index; j++) {
                            var other = moons.get(j);
                            assertTrue(distance(moon.centerX(), moon.centerY(), moon.centerZ(),
                                    other.centerX(), other.centerY(), other.centerZ()) >= r + other.radius() + 16 - 1e-8);
                        }
                    }
                }
            }
            assertEquals(b.satellitesAtCell(-35, -35), a.satellitesAtCell(-35, -35));
        }
        assertTrue(placed > 100);
        assertTrue(zeroCounts > 100);
    }

    @Test
    void legacyPathMatchesLiteralPreviousPlacementBitForBit() {
        for (int y : new int[]{-64, 62, 300, 319}) {
            for (int radius : new int[]{16, 128, 484, 512}) {
                for (boolean enabled : new boolean[]{false, true}) {
                    var params = new SphereLayout.Params(4000, y, radius, radius,
                            enabled, 3, 8, 12, 25, 75, 16, 30, 55);
                    var bounds = new SatellitePlacement.Bounds(-2000, 2000, -2000, 2000);
                    var oldBounds = new LegacySatellitePlacement.Bounds(-2000, 2000, -2000, 2000);
                    for (int cell = -100; cell <= 100; cell++) {
                        var parent = new SphereLayout.Sphere(cell, -cell, 0, y, 0, radius);
                        var neighbours = List.of(new SphereLayout.Sphere(99, 99, 300, y, 300, radius));
                        var expected = LegacySatellitePlacement.place(parent, neighbours, params, oldBounds);
                        assertEquals(expected, SatellitePlacement.place(parent, neighbours, params, bounds));
                        assertEquals(expected, SatellitePlacement.place(parent, neighbours, params, bounds, false));
                        if (!enabled || (y == 62 && radius == 16)) assertEquals(expected,
                                SatellitePlacement.place(parent, neighbours, params, bounds, true));
                    }
                }
            }
        }
    }

    @Test
    void heightFitKeepsSeedsSizesAzimuthAndRadialGaps() {
        var params = new SphereLayout.Params(4000, 62, 415, 415,
                true, 3, 8, 12, 25, 75, 16, 30, 55);
        var parent = new SphereLayout.Sphere(0, 0, 0, 62, 0, 415);
        var bounds = new SatellitePlacement.Bounds(-2000, 2000, -2000, 2000);
        var oldBounds = new LegacySatellitePlacement.Bounds(-2000, 2000, -2000, 2000);
        assertEquals(1, LegacySatellitePlacement.countAt(parent.cellX(), parent.cellZ(), 3));
        double lowestTop = Double.POSITIVE_INFINITY;
        for (int index = 0; index < 1; index++) {
            for (int attempt = 0; attempt < 16; attempt++) {
                var previous = LegacySatellitePlacement.candidate(parent, params, oldBounds, index, attempt);
                lowestTop = Math.min(lowestTop, previous.centerY() + previous.radius());
            }
        }
        assertTrue(lowestTop > 319);
        var fitted = SatellitePlacement.place(parent, List.of(), params, bounds, true);
        assertEquals(1, fitted.size());
        for (int index = 0; index < fitted.size(); index++) {
            var previous = LegacySatellitePlacement.candidate(parent, params, oldBounds, index, 0);
            var moon = fitted.get(index);
            assertEquals(previous.seed(), moon.seed());
            assertEquals(previous.radius(), moon.radius());
            assertEquals(Math.atan2(previous.centerZ(), previous.centerX()),
                    Math.atan2(moon.centerZ(), moon.centerX()), 1e-12);
            assertEquals(distance(previous.centerX(), previous.centerY(), previous.centerZ(), 0, 62, 0),
                    distance(moon.centerX(), moon.centerY(), moon.centerZ(), 0, 62, 0), 1e-10);
            assertTrue(moon.centerY() + moon.radius() <= 319);
            assertTrue(moon.centerY() - moon.radius() >= -64);
        }
    }

    @Test
    void heightFitStillRejectsImpossibleVolumesAndHorizontalBounds() {
        for (int y : new int[]{-64, 62, 300, 319}) {
            var params = new SphereLayout.Params(4000, y, 512, 512,
                    true, 64, 100, 100, 25, 75, 16, 30, 55);
            var parent = new SphereLayout.Sphere(1, 2, 0, y, 0, 512);
            assertTrue(SatellitePlacement.place(parent, List.of(), params,
                    new SatellitePlacement.Bounds(-2000, 2000, -2000, 2000), true).isEmpty());
        }
        var params = new SphereLayout.Params(120, 62, 484, 484,
                true, 64, 8, 12, 25, 75, 16, 30, 55);
        assertTrue(SatellitePlacement.place(new SphereLayout.Sphere(1, 2, 0, 62, 0, 484), List.of(), params,
                new SatellitePlacement.Bounds(-60, 60, -60, 60), true).isEmpty());
    }

    @Test
    void heightFitCanUseTheEquatorWhenItExactlyFits() {
        var params = new SphereLayout.Params(4000, 269, 500, 500,
                true, 64, 10, 10, 25, 25, 16, 30, 55);
        var parent = new SphereLayout.Sphere(1, 2, 0, 269, 0, 500);
        var moons = SatellitePlacement.place(parent, List.of(), params,
                new SatellitePlacement.Bounds(-2000, 2000, -2000, 2000), true);
        assertFalse(moons.isEmpty());
        for (var moon : moons) {
            assertEquals(269, moon.centerY());
            assertEquals(319, moon.centerY() + moon.radius());
        }
    }

    private static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    // Literal previous placement, independent of the implementation under test.
    static final class LegacySatellitePlacement {

        /** Limites verticales del mundo de la etapa 1. */
        public static final int WORLD_MIN_Y = -64;
        public static final int WORLD_MAX_Y = 319;

        /** Intentos por satelite antes de omitirlo sin error. */
        public static final int ATTEMPTS_PER_SATELLITE = 16;

        /** Separacion angular entre satelites consecutivos. */
        private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

        /** Jitter maximo del angulo horizontal, en radianes (medio valor). */
        private static final double ANGLE_JITTER = 0.25;

        private LegacySatellitePlacement() {
        }

        /** Limites horizontales de la celda de la esfera principal. */
        public record Bounds(double minX, double maxX, double minZ, double maxZ) {
            public Bounds {
                if (!(minX < maxX) || !(minZ < maxZ)) {
                    throw new IllegalArgumentException("invalid cell bounds");
                }
            }
        }

        /**
         * Coloca entre 0 y {@code params.satellitesMaxCount()} satelites.
         *
         * <p>El tamano y el hueco se sortean dentro de sus rangos; el hueco nunca baja
         * de {@code satellitesMinGapBlocks}. Cada candidato se valida con su volumen
         * completo: dentro de la celda (con margen), dentro de los limites verticales y
         * sin solape con la principal, las vecinas ni los satelites ya colocados. Si un
         * satelite agota los intentos, se omite y se sigue con el siguiente.</p>
         */
        public static List<SphereLayout.Satellite> place(SphereLayout.Sphere principal,
                                                         List<SphereLayout.Sphere> neighbours,
                                                         SphereLayout.Params params,
                                                         Bounds bounds) {
            if (!params.satellitesEnabled() || params.satellitesMaxCount() <= 0) {
                return List.of();
            }

            int count = countAt(principal.cellX(), principal.cellZ(), params.satellitesMaxCount());
            if (count == 0) {
                return List.of();
            }

            double margin = params.satellitesMinGapBlocks();
            List<SphereLayout.Satellite> placed = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                for (int attempt = 0; attempt < ATTEMPTS_PER_SATELLITE; attempt++) {
                    SphereLayout.Satellite candidate = candidate(principal, params, bounds, index, attempt);
                    if (candidate != null
                            && isValid(candidate, principal, neighbours, placed, params, bounds, margin)) {
                        placed.add(candidate);
                        break;
                    }
                }
            }
            return List.copyOf(placed);
        }

        private static int countAt(int cellX, int cellZ, int maxCount) {
            long hash = mix64(cellSeed(cellX, cellZ) ^ 0x6A09E667F3BCC909L);
            return (int) Math.floorMod(hash, (long) maxCount + 1L);
        }

        private static SphereLayout.Satellite candidate(SphereLayout.Sphere principal,
                                                        SphereLayout.Params params,
                                                        Bounds bounds,
                                                        int index,
                                                        int attempt) {
            int radius = principal.radius();
            double margin = params.satellitesMinGapBlocks();

            long seed = mix64(cellSeed(principal.cellX(), principal.cellZ())
                    ^ ((long) (index + 1) * 0x165667B19E3779F9L)
                    ^ ((long) (attempt + 1) * 0x27D4EB2F165667C5L));

            double elevationDegrees = params.satellitesMinElevationDegrees()
                    + unit(seed, 1) * (params.satellitesMaxElevationDegrees() - params.satellitesMinElevationDegrees());
            double elevation = Math.toRadians(elevationDegrees);

            int sizePercent = pickInt(seed, 2,
                    params.satellitesMinSizePercent(), params.satellitesMaxSizePercent());
            int satelliteRadius = Math.max(1, (int) Math.round(radius * sizePercent / 100.0));

            double gapPercent = params.satellitesMinGapPercent()
                    + unit(seed, 3) * (params.satellitesMaxGapPercent() - params.satellitesMinGapPercent());
            double gap = Math.max(margin, radius * gapPercent / 100.0);

            double distance = radius + gap + satelliteRadius;

            // Angulo base por celda + paso aureo por indice: los satelites de una misma
            // celda no pueden salir todos en la misma direccion.
            double baseAngle = unit(cellSeed(principal.cellX(), principal.cellZ()), 4) * Math.PI * 2.0;
            double angle = baseAngle + index * GOLDEN_ANGLE + (unit(seed, 5) - 0.5) * ANGLE_JITTER;

            double horizontal = distance * Math.cos(elevation);
            double centerX = principal.centerX() + horizontal * Math.cos(angle);
            double centerZ = principal.centerZ() + horizontal * Math.sin(angle);
            double centerY = principal.centerY() + distance * Math.sin(elevation);

            // El chequeo de celda se hace aqui tambien para descartar pronto; isValid lo
            // repite con el margen explicito.
            if (centerX - satelliteRadius < bounds.minX() + margin
                    || centerX + satelliteRadius > bounds.maxX() - margin
                    || centerZ - satelliteRadius < bounds.minZ() + margin
                    || centerZ + satelliteRadius > bounds.maxZ() - margin) {
                return null;
            }

            return new SphereLayout.Satellite(centerX, centerY, centerZ, satelliteRadius, seed);
        }

        private static boolean isValid(SphereLayout.Satellite candidate,
                                       SphereLayout.Sphere principal,
                                       List<SphereLayout.Sphere> neighbours,
                                       List<SphereLayout.Satellite> placed,
                                       SphereLayout.Params params,
                                       Bounds bounds,
                                       double margin) {
            int radius = candidate.radius();

            // Volumen completo dentro de los limites verticales del mundo.
            if (candidate.centerY() - radius < WORLD_MIN_Y || candidate.centerY() + radius > WORLD_MAX_Y) {
                return false;
            }

            // Volumen completo dentro de la celda horizontal, con el margen de seguridad.
            if (candidate.centerX() - radius < bounds.minX() + margin
                    || candidate.centerX() + radius > bounds.maxX() - margin
                    || candidate.centerZ() - radius < bounds.minZ() + margin
                    || candidate.centerZ() + radius > bounds.maxZ() - margin) {
                return false;
            }

            // Sin solape con la principal ni con las principales vecinas.
            if (distance(candidate, principal.centerX(), principal.centerY(), principal.centerZ())
                    < principal.radius() + radius + margin) {
                return false;
            }
            for (SphereLayout.Sphere neighbour : neighbours) {
                if (distance(candidate, neighbour.centerX(), neighbour.centerY(), neighbour.centerZ())
                        < neighbour.radius() + radius + margin) {
                    return false;
                }
            }

            // Sin solape con los satelites ya colocados de la misma celda.
            for (SphereLayout.Satellite other : placed) {
                if (distance(candidate, other.centerX(), other.centerY(), other.centerZ())
                        < other.radius() + radius + margin) {
                    return false;
                }
            }
            return true;
        }

        private static double distance(SphereLayout.Satellite satellite, double x, double y, double z) {
            double dx = satellite.centerX() - x;
            double dy = satellite.centerY() - y;
            double dz = satellite.centerZ() - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private static int pickInt(long seed, int salt, int min, int max) {
            if (min >= max) {
                return max;
            }
            int value = min + (int) Math.floor(unit(seed, salt) * (max - min + 1));
            return Math.min(value, max);
        }

        /** {@code [0,1)} a partir del hash y de una sal. */
        private static double unit(long seed, int salt) {
            long hash = mix64(seed + salt * 0x9E3779B97F4A7C15L);
            return (hash >>> 11) * 0x1.0p-53;
        }

        private static long cellSeed(int cellX, int cellZ) {
            return ((long) cellX * 0x9E3779B97F4A7C15L) ^ ((long) cellZ * 0xC2B2AE3D27D4EB4FL);
        }

        /** Mezcla de 64 bits (murmur3 finalizer). */
        private static long mix64(long value) {
            value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
            value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
            return value ^ (value >>> 33);
        }
    }
}
