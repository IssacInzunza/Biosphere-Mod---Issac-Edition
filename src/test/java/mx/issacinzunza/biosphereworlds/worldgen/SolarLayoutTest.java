package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class SolarLayoutTest {
    @Test
    void largeSpawnPlanetRecoversItsTwoMoonsOnlyInLayoutSeven() {
        long seed = -7284467477822061774L;
        var params = largeMoonParams(3);
        var options = new SolarLayout.PlacementOptions(62, 8, 12, 25, 75);
        var old = new SolarLayout(seed, params, 6, options).systemAtCell(0, 0);
        var current = new SolarLayout(seed, params, 7, options).systemAtCell(0, 0);
        assertEquals(old.bodies(), current.bodies());
        var spawn = current.bodies().stream().filter(b -> !b.central()
                && b.climate() == SolarLayout.Climate.TEMPERATE).findFirst().orElseThrow();
        assertEquals(current.bodies().get(4), spawn);
        assertEquals(484, spawn.radius());
        assertEquals(62, spawn.y());
        assertEquals(-4723902467518963098L, spawn.id());
        assertEquals(2, SatellitePlacementTest.requestedMoons(spawn.id(), 3));
        assertEquals(0, old.moons().stream().filter(m -> m.planetId() == spawn.id()).count());
        assertEquals(2, current.moons().stream().filter(m -> m.planetId() == spawn.id()).count());
        long firstId = current.bodies().get(1).id();
        assertEquals(0, SatellitePlacementTest.requestedMoons(firstId, 3));
        assertTrue(current.moons().stream().noneMatch(m -> m.planetId() == firstId));
        assertEquals(old.bodies(), new SolarLayout(seed, largeMoonParams(0), 7, options).systemAtCell(0, 0).bodies());
        assertTrue(new SolarLayout(seed, largeMoonParams(0), 7, options).systemAtCell(0, 0).moons().isEmpty());
    }

    @Test
    void fittedMoonsRespectFullVolumesGapsAndSystemEnvelope() {
        var params = largeMoonParams(3);
        int checked = 0;
        for (int y : new int[]{-64, -40, 62, 250, 319}) {
            for (long seed : new long[]{-7284467477822061774L, 0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                var layout = new SolarLayout(seed, params, 7, new SolarLayout.PlacementOptions(y, 8, 12, 25, 75));
                for (int cell = -3; cell <= 3; cell++) {
                    var system = layout.systemAtCell(cell, -cell);
                    for (var moon : system.moons()) {
                        checked++;
                        var s = moon.sphere();
                        var parent = system.bodies().stream().filter(b -> b.id() == moon.planetId()).findFirst().orElseThrow();
                        assertFalse(parent.central());
                        assertTrue(s.radius() < parent.radius());
                        assertTrue(s.centerY() - s.radius() >= -64);
                        assertTrue(s.centerY() + s.radius() <= 319);
                        assertTrue(Math.hypot(s.centerX() - system.central().x(), s.centerZ() - system.central().z())
                                + s.radius() <= params.systemRadius());
                        for (var body : system.bodies()) {
                            double gap = moonDistance(s, body.x(), body.y(), body.z()) - s.radius() - body.radius();
                            assertTrue(gap >= params.satelliteGap() - 1e-8);
                            if (body.id() == parent.id()) {
                                assertTrue(gap >= Math.max(16, parent.radius() * .25) - 1e-8);
                                assertTrue(gap <= Math.max(16, parent.radius() * .75) + 1e-8);
                            }
                        }
                        for (var other : system.moons()) {
                            if (other == moon) continue;
                            var s2 = other.sphere();
                            assertTrue(moonDistance(s, s2.centerX(), s2.centerY(), s2.centerZ())
                                    >= s.radius() + s2.radius() + params.satelliteGap() - 1e-8);
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100);
    }

    @Test
    void layoutSevenIsDeterministicAcrossOrderCacheEvictionAndThreads() throws Exception {
        var params = largeMoonParams(3);
        var options = new SolarLayout.PlacementOptions(62, 8, 12, 25, 75);
        var shared = new SolarLayout(-7284467477822061774L, params, 7, options);
        var tasks = new ArrayList<Callable<Void>>();
        for (int i = -300; i <= 300; i++) {
            int cell = i;
            tasks.add(() -> {
                assertEquals(new SolarLayout(-7284467477822061774L, params, 7, options).systemAtCell(cell, -cell),
                        shared.systemAtCell(cell, -cell));
                return null;
            });
        }
        Collections.shuffle(tasks, new java.util.Random(27));
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(tasks)) result.get();
        }
    }

    private static SolarLayout.Params largeMoonParams(int count) {
        return new SolarLayout.Params(SolarLayout.requiredSingleOrbitSystemRadius(8, 512, 180, 1000, 75, 12, 16),
                5000, 8, 256, 512, 180, 1000, 1000, false, 30, 40, 30, count, 30, 55, 16);
    }

    private static double moonDistance(SphereLayout.Satellite moon, double x, double y, double z) {
        return Math.sqrt(Math.pow(moon.centerX() - x, 2) + Math.pow(moon.centerY() - y, 2)
                + Math.pow(moon.centerZ() - z, 2));
    }

    @Test
    void distinctOrbitsKeepConfiguredHeightEvenBeyondBuildLimits() {
        int region = SolarLayout.requiredSingleOrbitSystemRadius(4, 512, 180, 400, 75, 12, 16);
        var p = new SolarLayout.Params(region, 1500, 4, 512, 512, 180, 400, 400, false, 30, 40, 30, 0, 30, 55, 16);
        for (int y : new int[]{-40, 62, 300}) {
            var layout = new SolarLayout(19, p, 6, new SolarLayout.PlacementOptions(y, 8, 12, 25, 75, true));
            for (var body : layout.systemAtCell(0, 0).bodies()) {
                if (!body.central()) assertEquals(y, body.y());
            }
        }
    }

    @Test
    void distinctOrbitsPreserveExistingAnglesHeightAndClimate() {
        for (int count = 1; count <= 16; count++) {
            int region = SolarLayout.requiredSingleOrbitSystemRadius(count, 160, 180, 400, 75, 12, 16);
            var p = new SolarLayout.Params(region, 1500, count, 24, 160, 180, 400, 400, false, 30, 40, 30, 3, 30, 55, 16);
            var placement = new SolarLayout.PlacementOptions(62, 8, 12, 25, 75, false);
            for (long seed : new long[]{0, 17, -4634224182739581737L}) {
                var old = new SolarLayout(seed, p, 5, placement).systemAtCell(-1, 2);
                var current = new SolarLayout(seed, p, 6, placement).systemAtCell(-1, 2);
                assertEquals(count + 1, current.bodies().size());
                double previousOrbit = 0;
                for (int i = 1; i <= count; i++) {
                    var body = current.bodies().get(i);
                    var before = old.bodies().get(i);
                    double x = body.x() - current.central().x();
                    double z = body.z() - current.central().z();
                    double orbit = Math.hypot(x, z);
                    assertTrue(orbit > previousOrbit);
                    assertTrue(orbit + body.radius() <= region);
                    assertEquals(62, body.y());
                    assertEquals(before.y(), body.y());
                    assertEquals(before.id(), body.id());
                    assertEquals(before.climate(), body.climate());
                    assertEquals(Math.atan2(before.z() - old.central().z(), before.x() - old.central().x()),
                            Math.atan2(z, x), 1e-12);
                    previousOrbit = orbit;
                    for (int j = 0; j < i; j++) {
                        var other = current.bodies().get(j);
                        assertTrue(Math.sqrt(Math.pow(body.x() - other.x(), 2) + Math.pow(body.y() - other.y(), 2)
                                + Math.pow(body.z() - other.z(), 2)) - body.radius() - other.radius() >= 400 - 1e-8);
                    }
                }
                var neighbor = new SolarLayout(seed, p, 6, placement).systemAtCell(0, 2).central();
                assertTrue(Math.hypot(neighbor.x() - current.central().x(), neighbor.z() - current.central().z())
                        >= 2.0 * region + p.systemGap());
                assertTrue(current.moons().stream().noneMatch(moon -> moon.planetId() == current.central().id()));
            }
        }
    }

    @Test
    void moonGeometryMatchesLiteralGenerationAndHeightmapBoundaries() {
        var random = new java.util.Random(1996);
        for (int radius : new int[]{1, 2, 15, 128, 512}) {
            var sphere = new SphereLayout.Satellite(-1234.5, 67.25, 2199.75, radius, 81);
            var moon = new SolarLayout.Moon(17, sphere);
            for (int sample = 0; sample < 50_000; sample++) {
                double x = sphere.centerX() + random.nextDouble() * radius * 4 - radius * 2;
                double y = sphere.centerY() + random.nextDouble() * radius * 4 - radius * 2;
                double z = sphere.centerZ() + random.nextDouble() * radius * 4 - radius * 2;
                // Literal formula used by both previous volume-writing paths.
                double previous = Math.pow(x - sphere.centerX(), 2) + Math.pow(y - sphere.centerY(), 2)
                        + Math.pow(z - sphere.centerZ(), 2);
                boolean inside = previous <= (double) sphere.radius() * sphere.radius();
                assertEquals(Double.doubleToRawLongBits(previous), Double.doubleToRawLongBits(moon.squaredDistanceTo(x, y, z)));
                assertEquals(inside, moon.containsSquaredDistance(previous));
                assertEquals(inside, moon.contains(x, y, z));
            }
            double edge = sphere.centerX() + radius;
            assertTrue(moon.contains(edge, sphere.centerY(), sphere.centerZ()));
            assertTrue(moon.contains(Math.nextDown(edge), sphere.centerY(), sphere.centerZ()));
            assertFalse(moon.contains(Math.nextUp(edge), sphere.centerY(), sphere.centerZ()));
        }
    }

    @Test
    void guardianCoverageIncludesPolesAndEverySampledSurfaceDirection() {
        // The farthest direction in an icosahedron's Voronoi cell is a face center.
        // This bound covers the continuous surface, including directions between samples.
        double phi = (1 + Math.sqrt(5)) / 2;
        double faceY = 1 + 2 * phi;
        double cosAngle = (faceY + phi * phi)
                / Math.sqrt((1 + phi * phi) * (faceY * faceY + phi * phi));
        for (int radius = 170; radius <= 190; radius++) {
            double orbit = radius + 24;
            double farthest = Math.sqrt(radius * radius + orbit * orbit - 2 * radius * orbit * cosAngle);
            // Rounded cage coordinates, block-center activation, and one block of player height.
            assertTrue(farthest + Math.sqrt(3) + 1 < SolarLayout.GUARDIAN_ACTIVATION_RANGE);
        }
        for (int radius : new int[]{170, 180, 190}) for (long seed = 0; seed < 30; seed++) {
            var p = SolarLayout.Params.defaults();
            var params = new SolarLayout.Params(p.systemRadius(), p.systemGap(), p.planetCount(), p.minRadius(), p.maxRadius(),
                    radius, p.minGap(), p.maxGap(), p.coldInside(), p.innerPercent(), p.middlePercent(), p.outerPercent(),
                    p.satelliteCount(), p.satelliteMinElevation(), p.satelliteMaxElevation(), p.satelliteGap());
            var layout = new SolarLayout(seed, params, 5, SolarLayout.PlacementOptions.defaults());
            var system = layout.systemAtCell(0, 0);
            var center = system.central();
            var posts = layout.guardianPosts(system);
            assertEquals(12, posts.size());
            assertTrue(posts.stream().anyMatch(post -> Math.pow(post.x() - center.x(), 2)
                    + Math.pow(post.y() - center.y(), 2) + Math.pow(post.z() - center.z(), 2)
                    <= (double) SolarLayout.GUARDIAN_ACTIVATION_RANGE * SolarLayout.GUARDIAN_ACTIVATION_RANGE));
            for (int latitude = -90; latitude <= 90; latitude += 3) for (int longitude = 0; longitude < 360; longitude += 3) {
                double a = Math.toRadians(latitude), b = Math.toRadians(longitude);
                double x = center.x() + radius * Math.cos(a) * Math.cos(b);
                double y = center.y() + radius * Math.sin(a);
                double z = center.z() + radius * Math.cos(a) * Math.sin(b);
                assertTrue(posts.stream().anyMatch(post -> Math.pow(post.x()-x,2)+Math.pow(post.y()-y,2)+Math.pow(post.z()-z,2)
                        <= (double) SolarLayout.GUARDIAN_ACTIVATION_RANGE * SolarLayout.GUARDIAN_ACTIVATION_RANGE), "Every approach, including both poles, must activate a cage");
            }
        }
    }

    @Test
    void newSunsHaveNoMoonsAndSafeSpawnerPostsWithoutMovingPlanets() {
        for (long seed = 0; seed < 100; seed++) {
            var oldLayout = new SolarLayout(seed, SolarLayout.Params.defaults(), 3, SolarLayout.PlacementOptions.defaults());
            var layout = new SolarLayout(seed, SolarLayout.Params.defaults(), 4, SolarLayout.PlacementOptions.defaults());
            var system = layout.systemAtCell(0, 0);
            assertEquals(oldLayout.systemAtCell(0, 0).bodies(), system.bodies());
            assertTrue(system.moons().stream().noneMatch(m -> m.planetId() == system.central().id()));
            var posts = layout.guardianPosts(system);
            assertEquals(3, posts.size());
            for (var post : posts) {
                for (var b : system.bodies()) assertTrue(Math.sqrt(Math.pow(post.x()-b.x(),2)+Math.pow(post.y()-b.y(),2)+Math.pow(post.z()-b.z(),2)) >= b.radius()+16);
                assertTrue(post.y()-1 >= SolarLayout.MIN_Y && post.y()+8 <= SolarLayout.MAX_Y);
            }
        }
    }

    @Test
    void configuredRadiusAndCenterCanExceedBuildHeightWithoutChangingSavedLayouts() {
        var params = new SolarLayout.Params(SolarLayout.requiredSystemRadius(8, 512, 180, 400, 75, 12, 16),
                1500, 8, 512, 512, 180, 400, 400, false, 30, 40, 30, 0, 30, 55, 16);
        var openOptions = new SolarLayout.PlacementOptions(62, 8, 12, 25, 75, false);
        var capOptions = new SolarLayout.PlacementOptions(62, 8, 12, 25, 75, true);
        var open = new SolarLayout(19, params, 3, openOptions);
        var closed = new SolarLayout(19, params, 3, capOptions);
        assertEquals(open.systemAtCell(0, 0), closed.systemAtCell(0, 0));
        for (var body : closed.systemAtCell(0, 0).bodies()) {
            if (body.central()) continue;
            assertEquals(512, body.radius());
            assertEquals(62, body.y());
            assertTrue(body.y() + body.radius() > SolarLayout.MAX_Y);
            assertTrue(closed.shellBoundary(body, (int) body.x(), SolarLayout.MAX_Y, (int) body.z()));
            assertFalse(open.shellBoundary(body, (int) body.x(), SolarLayout.MAX_Y, (int) body.z()));
        }
        var oldParams = SolarLayout.Params.defaults();
        assertEquals(new SolarLayout(19, oldParams, 2, openOptions).systemAtCell(0, 0),
                new SolarLayout(19, oldParams, 2, capOptions).systemAtCell(0, 0));
        var body = new SolarLayout(19, oldParams, 2, capOptions).systemAtCell(0, 0).bodies().get(1);
        assertFalse(new SolarLayout(19, oldParams, 2, capOptions).shellBoundary(body, (int) body.x(), SolarLayout.MAX_Y, (int) body.z()));
    }

    @Test
    void sharedSatelliteRangeRemainsBoundedAndRejectsParentSizedMoons() {
        var defaults = SolarLayout.Params.defaults();
        var params = new SolarLayout.Params(defaults.systemRadius(), 1500, 8, 128, 128, 180,
                400, 400, false, 30, 40, 30, 64, 0, 90, 16);
        var options = new SolarLayout.PlacementOptions(62, 100, 100, 25, 75);
        for (long seed = 0; seed < 10; seed++) {
            var layout = new SolarLayout(seed, params, 2, options);
            var system = layout.systemAtCell(0, 0);
            assertEquals(9, system.bodies().size());
            assertTrue(system.moons().isEmpty(), "A 100% moon must be omitted rather than matching its parent radius");
        }
    }

    @Test
    void completeVolumesStayInTheirRegionAndApart() {
        var params = SolarLayout.Params.defaults();
        for (long seed : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var layout = new SolarLayout(seed, params);
            for (int cx : new int[]{-5454, -2, -1, 0, 1, 2, 5454}) {
                for (int cz = -4; cz <= 4; cz++) {
                    var system = layout.systemAtCell(cx, cz);
                    var center = system.central();
                    assertEquals(1, system.bodies().stream().filter(SolarLayout.Body::central).count());
                    assertTrue(system.bodies().stream().anyMatch(b -> !b.central() && b.climate() == SolarLayout.Climate.TEMPERATE));
                    for (var body : system.bodies()) {
                        assertTrue(body.y() - body.radius() >= SolarLayout.MIN_Y);
                        assertTrue(body.y() + body.radius() <= SolarLayout.MAX_Y);
                        assertTrue(Math.hypot(body.x() - center.x(), body.z() - center.z()) + body.radius() <= params.systemRadius());
                        assertSame(body, layout.bodyAt(body.x(), body.y(), body.z(), 0));
                        for (var other : system.bodies()) {
                            if (body == other) continue;
                            assertTrue(Math.sqrt(Math.pow(body.x() - other.x(), 2) + Math.pow(body.y() - other.y(), 2) + Math.pow(body.z() - other.z(), 2))
                                    - body.radius() - other.radius() + 1e-9 >= params.minGap());
                        }
                    }
                    for (var moon : system.moons()) {
                        var s = moon.sphere();
                        var parent = system.bodies().stream().filter(b -> b.id() == moon.planetId()).findFirst().orElseThrow();
                        assertTrue(s.radius() < parent.radius());
                        assertTrue(s.centerY() > parent.y());
                        assertTrue(s.centerY() - s.radius() >= SolarLayout.MIN_Y);
                        assertTrue(s.centerY() + s.radius() <= SolarLayout.MAX_Y);
                        assertTrue(Math.hypot(s.centerX() - center.x(), s.centerZ() - center.z()) + s.radius() <= params.systemRadius());
                        for (var b : system.bodies()) {
                            double distance = Math.sqrt(Math.pow(s.centerX() - b.x(), 2) + Math.pow(s.centerY() - b.y(), 2)
                                    + Math.pow(s.centerZ() - b.z(), 2));
                            assertTrue(distance >= s.radius() + b.radius() + params.satelliteGap());
                        }
                        for (var other : system.moons()) {
                            if (moon == other) continue;
                            var a = other.sphere();
                            assertTrue(Math.sqrt(Math.pow(s.centerX() - a.centerX(), 2) + Math.pow(s.centerY() - a.centerY(), 2)
                                    + Math.pow(s.centerZ() - a.centerZ(), 2)) >= s.radius() + a.radius() + params.satelliteGap());
                        }
                    }
                    var next = layout.systemAtCell(cx + 1, cz).central();
                    assertTrue(next.x() - center.x() - 2 * params.systemRadius() >= params.systemGap());
                }
            }
        }
    }

    @Test
    void deterministicAcrossOrderCacheEvictionAndThreads() throws Exception {
        var params = SolarLayout.Params.defaults();
        var shared = new SolarLayout(87123, params);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = -400; i <= 400; i++) {
            int cell = i;
            tasks.add(() -> {
                assertEquals(new SolarLayout(87123, params).systemAtCell(cell, -cell), shared.systemAtCell(cell, -cell));
                return null;
            });
        }
        Collections.shuffle(tasks, new java.util.Random(27));
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(tasks)) result.get();
        }
        assertNotEquals(shared.systemAtCell(0, 0), new SolarLayout(87124, params).systemAtCell(0, 0));
    }

    @Test
    void directionOnlyExchangesExtremesAndAlwaysKeepsTemperate() {
        var hot = new SolarLayout(0, SolarLayout.Params.defaults());
        var cold = new SolarLayout(0, new SolarLayout.Params(2000, 1500, 8, 64, 112, 180, 100, 400, true, 30, 40, 30, 3, 30, 55, 16));
        for (int count = 1; count <= 16; count++) {
            boolean temperate = false;
            for (int rank = 0; rank < count; rank++) {
                var a = hot.climateAtRank(rank, count);
                var b = cold.climateAtRank(rank, count);
                if (a == SolarLayout.Climate.TEMPERATE) {
                    temperate = true;
                    assertEquals(a, b);
                } else assertNotEquals(a, b);
            }
            assertTrue(temperate);
        }
    }

    @Test
    void impossibleConfigurationIsRejectedAndFixedGapsWork() {
        assertThrows(IllegalArgumentException.class, () -> new SolarLayout.Params(512, 1500, 8, 160, 160, 180, 400, 100, false, 30, 40, 30, 3, 30, 55, 16));
        assertThrows(IllegalArgumentException.class, () -> new SolarLayout.Params(2000, 1500, 8, 64, 112, 180, 100, 400, false, 30, 20, 30, 3, 30, 55, 16));
        var layout = new SolarLayout(3, new SolarLayout.Params(2000, 1500, 8, 64, 112, 180, 100, 100, false, 30, 40, 30, 3, 30, 55, 16));
        assertTrue(layout.systemAtCell(0, 0).bodies().size() > 1);
    }

    @Test
    void satelliteToggleLeavesPlanetsBitForBitIdentical() {
        var p = SolarLayout.Params.defaults();
        var off = new SolarLayout.Params(p.systemRadius(), p.systemGap(), p.planetCount(), p.minRadius(), p.maxRadius(),
                p.centralRadius(), p.minGap(), p.maxGap(), p.coldInside(), p.innerPercent(), p.middlePercent(), p.outerPercent(),
                0, p.satelliteMinElevation(), p.satelliteMaxElevation(), p.satelliteGap());
        for (long seed = -10; seed < 10; seed++) {
            var enabled = new SolarLayout(seed, SolarLayout.Params.defaults());
            var disabled = new SolarLayout(seed, off);
            for (int cell = -10; cell < 10; cell++) {
                assertEquals(enabled.systemAtCell(cell, -cell).bodies(), disabled.systemAtCell(cell, -cell).bodies());
                assertTrue(disabled.systemAtCell(cell, -cell).moons().isEmpty());
            }
        }
    }

    @Test void orbitsSurroundTheCenterAndHonorTheRequestedPlanetCount() {
        for (int count = 1; count <= 16; count++) {
            int region = SolarLayout.requiredSystemRadius(count, 160, 180, 400, 75, 12, 16);
            var p = new SolarLayout.Params(region, 1500, count, 24, 160, 180, 400, 400, false, 30, 40, 30, 0, 30, 55, 16);
            for (long seed = 0; seed < 40; seed++) {
                var system = new SolarLayout(seed, p).systemAtCell(-1, 2);
                assertEquals(count + 1, system.bodies().size());
                var center = system.central();
                var angles = system.bodies().stream().filter(b -> !b.central())
                        .mapToDouble(b -> Math.atan2(b.z() - center.z(), b.x() - center.x())).sorted().toArray();
                if (count >= 3) {
                    double largest = angles[0] + Math.PI * 2 - angles[angles.length - 1];
                    for (int i = 1; i < angles.length; i++) largest = Math.max(largest, angles[i] - angles[i - 1]);
                    assertTrue(largest < Math.PI, "All planets fitted on one side of the center");
                }
                for (var b : system.bodies()) {
                    for (var other : system.bodies()) {
                        if (b == other) continue;
                        double separation = Math.sqrt(Math.pow(b.x() - other.x(), 2) + Math.pow(b.y() - other.y(), 2)
                                + Math.pow(b.z() - other.z(), 2)) - b.radius() - other.radius();
                        assertTrue(separation + 1e-8 >= 400);
                    }
                }
            }
        }
    }

    @Test void everyMiddleOrbitIsTemperateAndReversalPreservesPositions() {
        for (int count = 1; count <= 16; count++) {
            int region = SolarLayout.requiredSystemRadius(count, 128, 180, 400, 75, 12, 16);
            var hot = new SolarLayout.Params(region, 1500, count, 128, 128, 180, 400, 400, false, 30, 40, 30, 0, 30, 55, 16);
            var cold = new SolarLayout.Params(region, 1500, count, 128, 128, 180, 400, 400, true, 30, 40, 30, 0, 30, 55, 16);
            var a = new SolarLayout(4, hot).systemAtCell(0, 0);
            var b = new SolarLayout(4, cold).systemAtCell(0, 0);
            int rings = Math.min(3, count);
            int firstMiddle = rings == 1 ? 1 : 1 + count / rings + (count % rings > 0 ? 1 : 0);
            int middleCount = count / rings + (count % rings > 1 ? 1 : 0);
            for (int i = 0; i < a.bodies().size(); i++) {
                var x = a.bodies().get(i);
                var y = b.bodies().get(i);
                assertEquals(Double.doubleToLongBits(x.x()), Double.doubleToLongBits(y.x()));
                assertEquals(Double.doubleToLongBits(x.z()), Double.doubleToLongBits(y.z()));
                if (i >= firstMiddle && i < firstMiddle + middleCount) {
                    assertEquals(SolarLayout.Climate.TEMPERATE, x.climate());
                    assertEquals(x.climate(), y.climate());
                }
            }
        }
    }
}
