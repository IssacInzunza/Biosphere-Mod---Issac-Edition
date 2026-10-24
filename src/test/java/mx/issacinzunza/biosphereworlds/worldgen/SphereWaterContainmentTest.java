package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modelo puro conservador: el agua puede extenderse sin limite lateral y caer.
 * No reproduce niveles, ticks ni APIs de fluidos de Minecraft; comprueba la regla
 * para cada llegada al cascaron usando la geometria Solar existente.
 */
class SphereWaterContainmentTest {
    private static final int RADIUS = 8;
    private static final SolarLayout GEOMETRY = new SolarLayout(0, SolarLayout.Params.defaults());
    private static final int[][] STEPS = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    @Test
    void protectionOnlyAppliesToWaterAtAnOpenMainShell() {
        for (boolean upper : new boolean[]{false, true}) for (boolean lower : new boolean[]{false, true}) {
            for (int y : new int[]{61, 62, 63}) {
                assertFalse(SphereLayout.preserveShellForWater(false, true, true, y, 62, upper, lower), "Vanilla debe ser no-op");
                assertFalse(SphereLayout.preserveShellForWater(true, false, true, y, 62, upper, lower), "No intervenir en otros fluidos o aire seco");
                assertFalse(SphereLayout.preserveShellForWater(true, true, false, y, 62, upper, lower), "No escribir fuera del cascaron principal");
            }
        }
        assertTrue(SphereLayout.preserveShellForWater(true, true, true, 61, 62, true, false));
        assertFalse(SphereLayout.preserveShellForWater(true, true, true, 62, 62, true, false));
        assertTrue(SphereLayout.preserveShellForWater(true, true, true, 62, 62, false, true));
        assertFalse(SphereLayout.preserveShellForWater(true, true, true, 61, 62, false, true));
    }

    @Test
    void openShellLeaksInTheModelWithoutProtectionButRetainsWaterWithIt() {
        var body = body(0, 62, 0);
        var unprotected = simulate(body, true, false, false, false, false);
        var protectedResult = simulate(body, true, false, false, true, false);
        assertFalse(unprotected.escaped().isEmpty(), "Control negativo: el escenario debe permitir una fuga real en el modelo");
        assertTrue(protectedResult.escaped().isEmpty());
        assertFalse(protectedResult.protectedGlass().isEmpty(), "La prueba debe activar la contencion");
    }

    @Test
    void allFourCombinationsContainSidewaysFlowAndFalls() {
        var body = body(0, 62, 0);
        for (boolean upper : new boolean[]{false, true}) for (boolean lower : new boolean[]{false, true}) {
            var result = simulate(body, true, upper, lower, true, false);
            assertTrue(result.escaped().isEmpty(), "Fuga con superior=" + upper + " inferior=" + lower);
            assertFalse(result.water().isEmpty(), "La contencion no debe borrar el agua interior");
            for (var glass : result.protectedGlass()) {
                assertTrue(GEOMETRY.shellBoundary(body, glass.x(), glass.y(), glass.z()));
            }
        }
        var result = simulate(body, true, false, false, true, false);
        for (var wall : List.of(new Cell(6, 63, 0), new Cell(-6, 63, 0), new Cell(0, 63, 6), new Cell(0, 63, -6))) {
            assertTrue(result.protectedGlass().contains(wall), "Falta pared lateral en " + wall);
        }
        assertTrue(result.protectedGlass().contains(new Cell(0, 55, 0)), "Falta el fondo bajo el agua");
        assertFalse(result.glass().contains(new Cell(0, 69, 0)), "El cascaron seco superior debe seguir abierto");
    }

    @Test
    void existingWaterAtTheOuterEdgeMustSealItsSourceBeforeFlowingOutside() {
        var body = body(10, 62, -6);
        for (var source : List.of(new Cell(18, 62, -6), new Cell(10, 54, -6))) {
            var outside = source.y() == 54 ? source.offset(0, -1, 0) : source.offset(1, 0, 0);
            assertTrue(retain(body, source, true, false, false));
            assertFalse(retain(body, outside, true, false, false), "El destino exterior no sirve como barrera");
        }
    }

    @Test
    void globalCoordinatesProtectBothSidesOfPositiveAndNegativeChunkSeams() {
        // centro, borde protegido, vecino interior; las parejas cruzan un limite de chunk.
        int[][] cases = {{10, 16, 15}, {-6, 0, -1}, {5, -1, 0}, {21, 15, 16}};
        for (var entry : cases) {
            assertNotEquals(Math.floorDiv(entry[1], 16), Math.floorDiv(entry[2], 16));
            var alongX = simulate(body(entry[0], 62, 0), true, false, false, true, false);
            assertTrue(alongX.escaped().isEmpty());
            assertTrue(alongX.protectedGlass().contains(new Cell(entry[1], 63, 0)));
            var alongZ = simulate(body(0, 62, entry[0]), true, false, false, true, false);
            assertTrue(alongZ.escaped().isEmpty());
            assertTrue(alongZ.protectedGlass().contains(new Cell(0, 63, entry[1])));
        }
    }

    @Test
    void traversalOrderAndTranslationsDoNotChangeTheContainmentDecision() {
        for (var body : List.of(body(0, 62, 0), body(-6, 0, 10), body(10, 127, -6))) {
            for (boolean upper : new boolean[]{false, true}) for (boolean lower : new boolean[]{false, true}) {
                assertEquals(simulate(body, true, upper, lower, true, false),
                        simulate(body, true, upper, lower, true, true), "El orden de los pasos cambio el resultado");
            }
        }
    }

    @Test
    void vanillaAndFullyEnabledShellsKeepTheirPreviousModelExactly() {
        var body = body(10, 62, -6);
        assertEquals(simulate(body, true, true, true, false, false), simulate(body, true, true, true, true, false));
        assertEquals(simulate(body, false, false, false, false, false), simulate(body, false, false, false, true, false));
    }

    private static SolarLayout.Body body(int x, int y, int z) {
        return new SolarLayout.Body(0, x, y, z, RADIUS, false, SolarLayout.Climate.TEMPERATE);
    }

    private record Cell(int x, int y, int z) {
        Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }

    private record Result(Set<Cell> water, Set<Cell> glass, Set<Cell> protectedGlass, Set<Cell> escaped) { }

    private static boolean retain(SolarLayout.Body body, Cell cell, boolean biosphere, boolean upper, boolean lower) {
        return SphereLayout.preserveShellForWater(biosphere, true,
                GEOMETRY.shellBoundary(body, cell.x(), cell.y(), cell.z()), cell.y(), body.y(), upper, lower);
    }

    private static Result simulate(SolarLayout.Body body, boolean biosphere, boolean upper, boolean lower,
                                   boolean protection, boolean reverse) {
        var water = new HashSet<Cell>();
        var glass = new HashSet<Cell>();
        var protectedGlass = new HashSet<Cell>();
        var escaped = new HashSet<Cell>();
        for (int x = (int) body.x() - RADIUS; x <= body.x() + RADIUS; x++) {
            for (int y = (int) body.y() - RADIUS; y <= body.y() + RADIUS; y++) {
                for (int z = (int) body.z() - RADIUS; z <= body.z() + RADIUS; z++) {
                    if (GEOMETRY.shellBoundary(body, x, y, z) && SphereLayout.shellHemisphereEnabled(y, body.y(), upper, lower)) {
                        glass.add(new Cell(x, y, z));
                    }
                }
            }
        }
        var start = new Cell((int) body.x(), (int) body.y() + 1, (int) body.z());
        var pending = new ArrayDeque<Cell>();
        water.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            var source = reverse ? pending.removeLast() : pending.removeFirst();
            for (int i = 0; i < STEPS.length; i++) {
                var step = STEPS[reverse ? STEPS.length - 1 - i : i];
                var destination = source.offset(step[0], step[1], step[2]);
                if (glass.contains(destination)) continue;
                if (protection && retain(body, destination, biosphere, upper, lower)) {
                    glass.add(destination);
                    protectedGlass.add(destination);
                } else if (!body.contains(destination.x(), destination.y(), destination.z(), 0)) {
                    escaped.add(destination);
                } else if (water.add(destination)) {
                    pending.add(destination);
                }
            }
        }
        return new Result(Set.copyOf(water), Set.copyOf(glass), Set.copyOf(protectedGlass), Set.copyOf(escaped));
    }
}
