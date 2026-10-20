package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarTerrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolarTerrainTest {
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
