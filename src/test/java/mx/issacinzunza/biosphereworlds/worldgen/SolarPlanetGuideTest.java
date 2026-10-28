package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.List;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPlanetGuide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolarPlanetGuideTest {
    @Test void eachPlanetGetsExactlyHalfANightInEveryTwoDayCycle() {
        for (int slot = 0; slot < 4; slot++) {
            int visibleTicks = 0;
            int firstDay = -1;
            for (int tick = 0; tick < 48000; tick++) {
                float opacity = SolarPlanetGuide.opacity(tick, 0.5f, slot);
                assertTrue(opacity >= 0 && opacity <= 1);
                assertEquals(Float.floatToIntBits(opacity),
                        Float.floatToIntBits(SolarPlanetGuide.opacity(tick + 48000L, 0.5f, slot)));
                assertEquals(opacity, SolarPlanetGuide.opacity(tick - 48000L, 0.5f, slot));
                if (opacity > 0) {
                    visibleTicks++;
                    assertTrue(tick % 24000 >= 13000 && tick % 24000 < 23000);
                    if (firstDay < 0) firstDay = tick / 24000;
                    assertEquals(firstDay, tick / 24000);
                }
            }
            assertEquals(5000, visibleTicks);
        }
    }

    @Test void fadesSmoothlyAndAdjacentWindowsDoNotOverlap() {
        for (int slot = 0; slot < 4; slot++) {
            int start = 13000 + (slot / 2) * 24000 + (slot % 2) * 5000;
            assertEquals(0, SolarPlanetGuide.opacity(start, 0, slot));
            assertEquals(0.5f, SolarPlanetGuide.opacity(start + 100, 0, slot));
            assertEquals(1, SolarPlanetGuide.opacity(start + 200, 0, slot));
            assertEquals(1, SolarPlanetGuide.opacity(start + 4800, 0, slot));
            assertEquals(0.5f, SolarPlanetGuide.opacity(start + 4900, 0, slot));
            assertEquals(0, SolarPlanetGuide.opacity(start + 5000, 0, slot));
            float previous = 0;
            for (int tick = 0; tick <= 200; tick++) {
                float current = SolarPlanetGuide.opacity(start + tick, 0, slot);
                assertTrue(current >= previous);
                assertEquals(current, SolarPlanetGuide.opacity(start + 5000 - tick, 0, slot));
                previous = current;
            }
        }
        for (int tick = 0; tick < 48000; tick++) {
            int visibleSlots = 0;
            for (int slot = 0; slot < 4; slot++) {
                if (SolarPlanetGuide.opacity(tick, 0, slot) > 0) visibleSlots++;
            }
            assertTrue(visibleSlots <= 1);
        }
        assertEquals(0, SolarPlanetGuide.opacity(15000, 0, -1));
        assertEquals(0, SolarPlanetGuide.opacity(15000, 0, 4));
    }

    @Test void markersUseDestinationClimateAndNeverIncludeCenterOrCurrentPlanet() {
        var center = new SolarLayout.Body(1, 0, 100, 0, 180, true, SolarLayout.Climate.HOT);
        var warm = new SolarLayout.Body(2, 1000, 100, 0, 100, false, SolarLayout.Climate.HOT);
        var mild = new SolarLayout.Body(3, 0, 100, 1000, 100, false, SolarLayout.Climate.TEMPERATE);
        var cold = new SolarLayout.Body(4, -1000, 100, 0, 100, false, SolarLayout.Climate.COLD);
        var system = new SolarLayout.System(0, 0, 0, List.of(center, warm, mild, cold), List.of());
        var all = SolarPlanetGuide.markers(system, 500, 100, 500);
        assertEquals(List.of(
                new SolarPlanetGuide.Marker(1000, 0, 0xFFB574, 0),
                new SolarPlanetGuide.Marker(0, 1000, 0xA5D6A7, 1),
                new SolarPlanetGuide.Marker(-1000, 0, 0xB3DFFF, 2)), all);
        assertEquals(List.of(all.get(1), all.get(2)), SolarPlanetGuide.markers(system, 1000, 100, 0));
        assertEquals(List.of(all.get(0), all.get(2)), SolarPlanetGuide.markers(system, 0, 100, 1000));
        assertEquals(all, SolarPlanetGuide.markers(system, 1000, 301, 0));
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
        assertEquals(List.of(center, warm, mild, cold), system.bodies());
    }

    @Test void everySavedLayoutVersionUsesOnlyItsOwnPlanetsWithStableSlots() {
        int region = SolarLayout.requiredSingleOrbitSystemRadius(8, 128, 180, 400, 75, 12, 16);
        var defaults = new SolarLayout.Params(region, 1500, 8, 128, 128, 180, 400, 400,
                false, 30, 40, 30, 3, 30, 55, 16);
        for (int version = 1; version <= 7; version++) {
            var layout = new SolarLayout(713, defaults, version, SolarLayout.PlacementOptions.defaults());
            for (int cell : new int[]{-3, 0, 2}) {
                var system = layout.systemAtCell(cell, -2);
                var center = system.central();
                var markers = SolarPlanetGuide.markers(system, center.x(), center.y(), center.z());
                var planets = system.bodies().stream().filter(body -> !body.central()).toList();
                assertEquals(planets.size(), markers.size());
                for (int index = 0; index < planets.size(); index++) {
                    var planet = planets.get(index);
                    var marker = markers.get(index);
                    assertEquals(planet.x(), marker.x());
                    assertEquals(planet.z(), marker.z());
                    assertEquals(SolarPlanetGuide.color(planet.climate()), marker.color());
                    assertEquals(index % 4, marker.slot());
                    var fromPlanet = SolarPlanetGuide.markers(system, planet.x(), planet.y(), planet.z());
                    assertFalse(fromPlanet.contains(marker));
                    assertTrue(markers.containsAll(fromPlanet));
                }
            }
        }
    }
}
