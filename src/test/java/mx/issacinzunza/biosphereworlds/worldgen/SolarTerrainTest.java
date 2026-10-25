package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarTerrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolarTerrainTest {
    @Test
    void exposedLinerMovesToTheOuterSurfaceAndKeepsTheCentralCeiling() {
        for (boolean central : new boolean[]{false, true}) for (int centerY : new int[]{62, 127, 300}) {
            var body = new SolarLayout.Body(1, 0, centerY, 0, 180, central, SolarLayout.Climate.HOT);
            for (int x = -181; x <= 181; x += 2) for (int y = -64; y <= 319; y++) {
                double distanceSq = (double) x * x + (double) (y - centerY) * (y - centerY);
                boolean expected = y <= (central ? Math.min(centerY, 63) : centerY)
                        && distanceSq > 177.0 * 177.0 && distanceSq <= 180.0 * 180.0;
                assertEquals(expected, SolarTerrain.bedrockLiner(body, x, y, 0, true, true));
                assertEquals(SolarTerrain.bedrockLiner(body, x, y, 0, true),
                        SolarTerrain.bedrockLiner(body, x, y, 0, true, false));
                assertFalse(SolarTerrain.bedrockLiner(body, x, y, 0, false, true));
            }
        }
    }

    /** Literal predicate before the central liner ceiling correction. */
    private static boolean previousLiner(SolarLayout.Body body, int x, int y, int z, boolean enabled) {
        return enabled && y <= body.y() && body.contains(x, y, z, 2) && !body.contains(x, y, z, 5);
    }

    @Test
    void centralLinerStopsAtTerrainLevelAndLeavesAllOtherCasesIdentical() {
        for (boolean central : new boolean[]{false, true}) {
            for (int centerY : new int[]{62, 127, 300}) {
                var body = new SolarLayout.Body(1, 0, centerY, 0, 180, central, SolarLayout.Climate.HOT);
                int removed = 0;
                int preserved = 0;
                for (int x = -180; x <= 180; x += 2) {
                    for (int y = -64; y <= 319; y++) {
                        boolean previous = previousLiner(body, x, y, 0, true);
                        boolean current = SolarTerrain.bedrockLiner(body, x, y, 0, true);
                        if (central && y > 63) {
                            assertFalse(current);
                            if (previous) removed++;
                        } else {
                            assertEquals(previous, current);
                            if (current) preserved++;
                        }
                        assertEquals(previousLiner(body, x, y, 0, false), SolarTerrain.bedrockLiner(body, x, y, 0, false));
                    }
                }
                if (!central || centerY - body.radius() + 2 <= 63) assertTrue(preserved > 0);
                else assertEquals(0, preserved);
                if (central && centerY > 63) assertTrue(removed > 0);
            }
        }
    }

    @Test
    void namedReservationsPreserveLiteralSurfaceRules() {
        for (int surface = -64; surface <= 319; surface++) {
            for (int y = -64; y <= 319; y++) {
                assertEquals(y >= 56 && y <= surface && y >= surface - 5, SolarTerrain.centralSurface(y, surface));
                assertEquals(y >= 56, SolarTerrain.centralFluid(y, true));
                assertFalse(SolarTerrain.centralFluid(y, false));
            }
        }
        assertEquals(48, SolarTerrain.STRONGHOLD_MAX_Y);
        assertEquals(5, SolarTerrain.STRONGHOLD_INTERIOR_MARGIN);
    }

    @Test void centralHazardsStayAboveTheCompleteStrongholdVolume() {
        for (int surface = 56; surface <= 319; surface++) {
            for (int y = -64; y <= 48; y++) {
                assertFalse(SolarTerrain.centralSurface(y, surface));
                assertFalse(SolarTerrain.centralFluid(y, true));
            }
        }
        assertTrue(SolarTerrain.centralSurface(75, 75));
        assertTrue(SolarTerrain.centralSurface(70, 75));
        assertFalse(SolarTerrain.centralSurface(69, 75));
        assertTrue(SolarTerrain.centralFluid(63, true));
    }
    @Test void disabledOptionsPreserveLiteralPreviousBlockIds() {
        var body = new SolarLayout.Body(1, 0, 64, 0, 112, false, SolarLayout.Climate.TEMPERATE);
        // Literal baseline before oceans/lining: keep the input block without any transformation.
        for (int x = -120; x <= 120; x += 3) {
            for (int y = -64; y <= 319; y++) {
                long original = ((long) x << 32) ^ y;
                long actual = original;
                if (SolarTerrain.legacyOceanOverride(body, x, y, 0, false, 20, 20, 63) != 0) actual = 17;
                if (SolarTerrain.bedrockLiner(body, x, y, 0, false)) actual = 18;
                assertEquals(original, actual);
            }
        }
    }

    @Test void oceanOnlyChangesInteriorBandAboveItsFloor() {
        var body = new SolarLayout.Body(1, 0, 64, 0, 112, false, SolarLayout.Climate.TEMPERATE);
        assertEquals(0, SolarTerrain.legacyOceanOverride(body, 0, 64, 0, true, 20, 20, 63));
        assertEquals(0, SolarTerrain.legacyOceanOverride(body, 100, 43, 0, true, 20, 20, 63));
        assertEquals(2, SolarTerrain.legacyOceanOverride(body, 100, 63, 0, true, 20, 20, 63));
        assertEquals(1, SolarTerrain.legacyOceanOverride(body, 100, 64, 0, true, 20, 20, 63));
        assertEquals(0, SolarTerrain.legacyOceanOverride(body, 112, 64, 0, true, 20, 20, 63));
        assertTrue(SolarTerrain.bedrockLiner(body, 0, -45, 0, true));
        assertFalse(SolarTerrain.bedrockLiner(body, 0, 173, 0, true));
    }
}
