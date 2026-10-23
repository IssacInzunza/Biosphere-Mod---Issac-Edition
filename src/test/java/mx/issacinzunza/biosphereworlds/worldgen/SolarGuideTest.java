package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolarGuideTest {
    @Test void hidesAtAndBeyondEnvelopeIncludingSpaceBetweenSystems() {
        int region = SolarLayout.requiredSingleOrbitSystemRadius(8, 128, 180, 400, 75, 12, 16);
        var params = new SolarLayout.Params(region, 1500, 8, 128, 128, 180, 400, 400,
                false, 30, 40, 30, 3, 30, 55, 16);
        for (int version = 1; version <= 6; version++) {
            var layout = new SolarLayout(71, params, version, SolarLayout.PlacementOptions.defaults());
            for (int cell : new int[]{-4, 0, 3}) {
                var center = layout.systemAtCell(cell, -2).central();
                int radius = layout.params().systemRadius();
                assertEquals(1, SolarLayout.guideOpacity(center.x() + radius - 64, center.z(), center.x(), center.z(), radius));
                assertEquals(0, SolarLayout.guideOpacity(center.x() + radius, center.z(), center.x(), center.z(), radius));
                assertEquals(0, SolarLayout.guideOpacity(center.x(), center.z() - radius - 1, center.x(), center.z(), radius));
                var next = layout.systemAtCell(cell + 1, -2).central();
                double x = (center.x() + next.x()) / 2;
                double z = (center.z() + next.z()) / 2;
                assertEquals(0, SolarLayout.guideOpacity(x, z, center.x(), center.z(), radius));
                assertEquals(0, SolarLayout.guideOpacity(x, z, next.x(), next.z(), radius));
            }
        }
    }

    @Test void fadesMonotonicallyAndHasNoBearingAtExactCenter() {
        assertEquals(0, SolarLayout.guideOpacity(10, -20, 10, -20, 512));
        assertEquals(0.5f, SolarLayout.guideOpacity(10 + 8, -20, 10, -20, 512));
        assertEquals(0.5f, SolarLayout.guideOpacity(10 + 512 - 32, -20, 10, -20, 512));
        assertEquals(0, SolarLayout.guideOpacity(0, 0, 0, 0, 0));
        float previous = 1;
        for (int distance = 448; distance <= 520; distance++) {
            float opacity = SolarLayout.guideOpacity(-1000, -1000 - distance, -1000, -1000, 512);
            assertTrue(opacity >= 0 && opacity <= previous);
            previous = opacity;
        }
    }
}
