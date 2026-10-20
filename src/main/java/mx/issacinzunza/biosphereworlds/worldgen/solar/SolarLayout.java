package mx.issacinzunza.biosphereworlds.worldgen.solar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import mx.issacinzunza.biosphereworlds.worldgen.SatellitePlacement;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;

/** Geometría Solar pura, por mundo. No consulta configuración global ni SphereLayout.current(). */
public final class SolarLayout {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    private static final int ATTEMPTS = 64;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    public enum Climate { HOT, TEMPERATE, COLD }

    public record Params(int systemRadius, int systemGap, int planetCount,
                         int minRadius, int maxRadius, int centralRadius,
                         int minGap, int maxGap, boolean coldInside,
                         int innerPercent, int middlePercent, int outerPercent,
                         int satelliteCount, int satelliteMinElevation, int satelliteMaxElevation, int satelliteGap) {
        public Params {
            if (systemRadius < 512 || systemGap < 0 || systemGap > 16000
                    || planetCount < 1 || planetCount > 16 || minRadius < 1
                    || minRadius > maxRadius || centralRadius < 170 || centralRadius > 190
                    || minGap < 0 || minGap > maxGap || maxGap > 4000
                    || innerPercent < 0 || middlePercent < 1 || outerPercent < 0
                    || innerPercent + middlePercent + outerPercent != 100
                    || satelliteCount < 0 || satelliteCount > 64 || satelliteMinElevation < 0
                    || satelliteMinElevation > satelliteMaxElevation || satelliteMaxElevation > 90
                    || satelliteGap < 0 || satelliteGap > 1024
                    || systemRadius < centralRadius + (double) minGap + 2.0 * maxRadius) {
                throw new IllegalArgumentException("Invalid Solar geometry or climate proportions");
            }
        }

        public static Params defaults() {
            return new Params(requiredSystemRadius(8, 128, 180, 400, 75, 12, 16), 1500, 8, 128, 128, 180, 400, 400, false, 30, 40, 30, 3, 30, 55, 16);
        }

        // A complete system stays inside its cell, with at least systemGap between envelopes.
        public int centerJitterChunks() { return Math.max(1, systemRadius / 128); }
        public int spacing() {
            return Math.toIntExact(((2L * systemRadius + systemGap + (long) centerJitterChunks() * 32 + 31) / 16) * 16);
        }
    }

    /**
     * Pure placement values; no Minecraft registries or global configuration are accessed.
     * @param centerY orbital center Y, in global blocks
     * @param minSize minimum moon radius as a percentage of its parent's radius
     * @param maxSize maximum moon radius as a percentage of its parent's radius
     * @param minGapPercent minimum moon gap as a percentage of its parent's radius
     * @param maxGapPercent maximum moon gap as a percentage of its parent's radius
     * @param closeOpenTop whether spheres crossing the build ceiling receive a cap
     */
    public record PlacementOptions(int centerY, int minSize, int maxSize, int minGapPercent, int maxGapPercent, boolean closeOpenTop) {
        public PlacementOptions(int centerY, int minSize, int maxSize, int minGapPercent, int maxGapPercent) {
            this(centerY, minSize, maxSize, minGapPercent, maxGapPercent, false);
        }
        public PlacementOptions {
            if (centerY < MIN_Y || centerY > MAX_Y || minSize < 1 || minSize > maxSize || maxSize > 100
                    || minGapPercent < 0 || minGapPercent > maxGapPercent || maxGapPercent > 1000) {
                throw new IllegalArgumentException("Invalid Solar general placement settings");
            }
        }
        public static PlacementOptions defaults() { return new PlacementOptions(64, 8, 12, 25, 75); }
    }

    /** Conservative envelope for three well-separated orbital bands, including moon space. */
    public static int requiredSystemRadius(int count, int maxRadius, int centralRadius, int gap,
                                          int moonGapPercent, int moonSizePercent, int moonGapBlocks) {
        int rings = Math.min(3, count);
        int perRing = (count + rings - 1) / rings;
        double inner = (double) centralRadius + maxRadius + gap;
        if (perRing > 1) inner = Math.max(inner, (2.0 * maxRadius + gap) / (2 * Math.sin(Math.PI * 0.8 / perRing)));
        double outer = inner + (rings - 1) * (2.0 * maxRadius + gap);
        double moonSpace = maxRadius + Math.max(moonGapBlocks, maxRadius * moonGapPercent / 100.0)
                + 2 * Math.ceil(maxRadius * moonSizePercent / 100.0);
        return Math.max(512, (int) Math.ceil(outer + Math.max(maxRadius, moonSpace) + 16));
    }

    public record Body(long id, double x, double y, double z, int radius, boolean central,
                       Climate climate) {
        public boolean contains(double px, double py, double pz, double margin) {
            double r = radius - margin;
            return r >= 0 && squared(px - x, py - y, pz - z) <= r * r;
        }

        public boolean containsBox(int x0, int y0, int z0, int x1, int y1, int z1, double margin) {
            double dx = Math.max(Math.abs(x0 - x), Math.abs(x1 - x));
            double dy = Math.max(Math.abs(y0 - y), Math.abs(y1 - y));
            double dz = Math.max(Math.abs(z0 - z), Math.abs(z1 - z));
            double r = radius - margin;
            return r >= 0 && squared(dx, dy, dz) <= r * r;
        }
    }

    /**
     * Immutable moon geometry with its parent planet's stable id.
     * @param planetId id of the parent orbital body
     * @param sphere satellite coordinates, radius and mineral-selection seed
     */
    public record Moon(long planetId, SphereLayout.Satellite sphere) {
        /**
         * Preserves the original squared-distance arithmetic used by blocks and heightmaps.
         * @param x finite global block X coordinate
         * @param y finite global block Y coordinate
         * @param z finite global block Z coordinate
         * @return squared distance to the moon center, in square blocks
         */
        public double squaredDistanceTo(double x, double y, double z) {
            return Math.pow(x - sphere.centerX(), 2) + Math.pow(y - sphere.centerY(), 2)
                    + Math.pow(z - sphere.centerZ(), 2);
        }

        /**
         * Reuses a distance already computed for mineral-layer selection.
         * @param squaredDistance nonnegative squared distance to this moon's center
         * @return whether that distance lies on or inside the moon's boundary
         */
        public boolean containsSquaredDistance(double squaredDistance) {
            return squaredDistance <= (double) sphere.radius() * sphere.radius();
        }

        /**
         * Tests the same inclusive boundary used by moon block generation.
         * @param x finite global block X coordinate
         * @param y finite global block Y coordinate
         * @param z finite global block Z coordinate
         * @return whether the point is on or inside the moon
         */
        public boolean contains(double x, double y, double z) {
            return containsSquaredDistance(squaredDistanceTo(x, y, z));
        }
    }
    public record System(long id, int cellX, int cellZ, List<Body> bodies, List<Moon> moons) {
        public System { bodies = List.copyOf(bodies); moons = List.copyOf(moons); }
        public Body central() { return bodies.getFirst(); }
    }

    private final long seed;
    private final Params params;
    private final int version;
    private final PlacementOptions options;
    private final AtomicReferenceArray<System> cache = new AtomicReferenceArray<>(256);
    private record GuardianSites(long systemId, List<GuardianPost> posts) { }
    private final AtomicReferenceArray<GuardianSites> guardianCache = new AtomicReferenceArray<>(256);

    public SolarLayout(long seed, Params params) {
        this(seed, params, 2, PlacementOptions.defaults());
    }

    public SolarLayout(long seed, Params params, int version, PlacementOptions options) {
        if (version < 1 || version > 5) throw new IllegalArgumentException("Unknown Solar layout version");
        this.seed = seed;
        this.params = params;
        this.version = version;
        this.options = options;
    }

    /** Shell and optional world-height cap share one boundary test across generation and heightmaps. */
    public boolean shellBoundary(Body body, int x, int y, int z) {
        return body.contains(x, y, z, 0) && (!body.contains(x, y, z, 2)
                || (version >= 3 && options.closeOpenTop && y == MAX_Y && body.y + body.radius > MAX_Y));
    }

    public Params params() { return params; }
    public int version() { return version; }

    public System systemAtBlock(double x, double z) {
        return systemAtCell((int) Math.floor(x / params.spacing()), (int) Math.floor(z / params.spacing()));
    }

    public System systemAtCell(int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        int slot = (int) mix(key) & 255;
        System cached = cache.get(slot);
        if (cached != null && cached.cellX == cellX && cached.cellZ == cellZ) return cached;
        System result = createSystem(cellX, cellZ, key);
        cache.set(slot, result);
        return result;
    }

    public Body bodyAt(double x, double y, double z, double margin) {
        for (Body body : systemAtBlock(x, z).bodies) {
            if (body.contains(x, y, z, margin)) return body;
        }
        return null;
    }

    public Climate climateAtRank(int rank, int count) {
        if (rank < 0 || rank >= count) throw new IllegalArgumentException("Invalid orbital rank");
        int middleRank = count / 2;
        double position = (rank + 0.5) / count;
        if (rank == middleRank || (position >= params.innerPercent / 100.0
                && position < (params.innerPercent + params.middlePercent) / 100.0)) {
            return Climate.TEMPERATE;
        }
        boolean inner = position < params.innerPercent / 100.0;
        return inner != params.coldInside ? Climate.HOT : Climate.COLD;
    }

    public Body centralAtCell(int cellX, int cellZ) {
        Body central = centralAtCell(seed, params, cellX, cellZ);
        return version == 1 ? central : new Body(central.id, central.x, central.y, central.z, central.radius, true,
                params.coldInside ? Climate.COLD : Climate.HOT);
    }

    public static Body centralAtCell(long seed, Params params, int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        long id = mix(seed ^ mix(key) ^ 0x14EBD63B570A125DL);
        int jitter = params.centerJitterChunks();
        double x = ((double) cellX * (params.spacing() / 16) + params.spacing() / 32
                + Math.floorMod(mix(id + 7), 2L * jitter + 1) - jitter) * 16 + 8;
        double z = ((double) cellZ * (params.spacing() / 16) + params.spacing() / 32
                + Math.floorMod(mix(id + 8), 2L * jitter + 1) - jitter) * 16 + 8;
        return new Body(mix(id), x, 127, z, params.centralRadius, true, Climate.TEMPERATE);
    }

    private System createSystem(int cellX, int cellZ, long key) {
        if (version >= 2) return createOrbitalSystem(cellX, cellZ, key);
        long id = mix(seed ^ mix(key) ^ 0x14EBD63B570A125DL);
        Body central = centralAtCell(cellX, cellZ);
        double x = central.x;
        double z = central.z;
        List<Body> bodies = new ArrayList<>();
        bodies.add(central);
        List<Body> orbitals = new ArrayList<>();
        for (int index = 0; index < params.planetCount; index++) {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                long hash = mix(id + 0x9E3779B97F4A7C15L * (1L + index * ATTEMPTS + attempt));
                int radius = params.minRadius + (int) Math.floorMod(hash, (long) params.maxRadius - params.minRadius + 1);
                Body anchor = bodies.get((int) Math.floorMod(mix(hash + 3), bodies.size()));
                double distance = anchor.radius + radius + params.minGap
                        + unit(mix(hash)) * (params.maxGap - params.minGap);
                double angle = unit(id) * 2 * Math.PI + index * GOLDEN_ANGLE + unit(mix(hash + 1)) * 2 * Math.PI;
                double centerY = Math.max(64, radius - 64);
                double horizontal = Math.sqrt(distance * distance - (centerY - anchor.y) * (centerY - anchor.y));
                Body candidate = new Body(mix(hash + 2), anchor.x + Math.cos(angle) * horizontal, centerY,
                        anchor.z + Math.sin(angle) * horizontal, radius, false, Climate.TEMPERATE);
                if (Math.hypot(candidate.x - x, candidate.z - z) + radius > params.systemRadius) continue;
                boolean valid = true;
                for (Body other : bodies) {
                    double gap = Math.sqrt(squared(candidate.x - other.x, candidate.y - other.y, candidate.z - other.z))
                            - radius - other.radius;
                    if (gap + 1e-9 < params.minGap) { valid = false; break; }
                }
                // The configured maximum is the gap to the nearest body, not to every body.
                double closest = Double.POSITIVE_INFINITY;
                for (Body other : bodies) {
                    closest = Math.min(closest, Math.sqrt(squared(candidate.x - other.x, candidate.y - other.y, candidate.z - other.z))
                            - radius - other.radius);
                }
                if (valid && closest <= params.maxGap + 1e-9) {
                    bodies.add(candidate);
                    orbitals.add(candidate);
                    break;
                }
            }
        }
        // A validated configuration always fits this smallest planet; no random retries are needed.
        if (orbitals.isEmpty()) {
            int radius = params.minRadius;
            double y = Math.max(64, radius - 64);
            double distance = central.radius + radius + params.minGap;
            double horizontal = Math.sqrt(distance * distance - (y - central.y) * (y - central.y));
            double angle = unit(mix(id + 9)) * 2 * Math.PI;
            orbitals.add(new Body(mix(id + 10), x + horizontal * Math.cos(angle), y,
                    z + horizontal * Math.sin(angle), radius, false, Climate.TEMPERATE));
        }
        // Stable fallback: fewer planets when the requested packing cannot be achieved.
        orbitals.sort(java.util.Comparator.comparingDouble(b -> squared(b.x - x, 0, b.z - z)));
        List<Body> result = new ArrayList<>();
        result.add(central);
        for (int i = 0; i < orbitals.size(); i++) {
            Body b = orbitals.get(i);
            result.add(new Body(b.id, b.x, b.y, b.z, b.radius, false, climateAtRank(i, orbitals.size())));
        }
        return new System(id, cellX, cellZ, result, moons(result));
    }

    private System createOrbitalSystem(int cellX, int cellZ, long key) {
        long id = mix(seed ^ mix(key) ^ 0x14EBD63B570A125DL);
        Body central = centralAtCell(cellX, cellZ);
        List<Body> planets = new ArrayList<>();
        planets.add(central);
        int rings = Math.min(3, params.planetCount);
        int mostPerRing = (params.planetCount + rings - 1) / rings;
        double inner = (double) central.radius + params.maxRadius + params.minGap;
        if (mostPerRing > 1) {
            // Small angular jitter still leaves 80% of an equal sector between neighboring centers.
            inner = Math.max(inner, (2.0 * params.maxRadius + params.minGap) / (2 * Math.sin(Math.PI * 0.8 / mostPerRing)));
        }
        double phase = unit(mix(id + 21)) * Math.PI * 2;
        int index = 0;
        for (int ring = 0; ring < rings; ring++) {
            int count = params.planetCount / rings + (ring < params.planetCount % rings ? 1 : 0);
            double orbit = inner + ring * (2.0 * params.maxRadius + params.minGap);
            double sector = Math.PI * 2 / count;
            double offset = params.planetCount <= 3 ? ring * Math.PI * 2 / params.planetCount : ring * GOLDEN_ANGLE;
            for (int slot = 0; slot < count; slot++) {
                long hash = mix(id + 0x9E3779B97F4A7C15L * (index + 1));
                int radius = params.minRadius + (int) Math.floorMod(hash, (long) params.maxRadius - params.minRadius + 1);
                double angle = phase + offset + slot * sector
                        + (unit(mix(hash + 1)) - 0.5) * Math.min(sector, Math.PI * 2 / params.planetCount) * 0.2;
                double y = version >= 3 ? options.centerY : Math.max(MIN_Y + radius, Math.min(MAX_Y - radius, options.centerY));
                Body body = new Body(mix(hash + 2), central.x + Math.cos(angle) * orbit, y,
                        central.z + Math.sin(angle) * orbit, radius, false,
                        ring == rings / 2 ? Climate.TEMPERATE : (ring == 0) != params.coldInside ? Climate.HOT : Climate.COLD);
                if (orbit + radius > params.systemRadius) {
                    throw new IllegalArgumentException("Solar region too small for the configured orbital separation");
                }
                planets.add(body);
                index++;
            }
        }
        return new System(id, cellX, cellZ, planets, moons(planets));
    }

    private List<Moon> moons(List<Body> planets) {
        if (params.satelliteCount == 0) return List.of();
        List<Moon> result = new ArrayList<>();
        var central = planets.getFirst();
        var bounds = new SatellitePlacement.Bounds(central.x - params.systemRadius, central.x + params.systemRadius,
                central.z - params.systemRadius, central.z + params.systemRadius);
        var placement = new SphereLayout.Params(params.spacing(), 127, params.minRadius, params.maxRadius,
                true, params.satelliteCount, options.minSize, options.maxSize, options.minGapPercent, options.maxGapPercent, params.satelliteGap,
                params.satelliteMinElevation, params.satelliteMaxElevation);
        var spheres = planets.stream().map(b -> new SphereLayout.Sphere((int) b.id, (int) (b.id >>> 32),
                b.x, b.y, b.z, b.radius)).toList();
        for (int i = 0; i < planets.size(); i++) {
            if (version >= 4 && planets.get(i).central()) continue;
            for (var moon : SatellitePlacement.place(spheres.get(i), spheres, placement, bounds)) {
                if (moon.radius() >= planets.get(i).radius) continue;
                if (Math.hypot(moon.centerX() - central.x, moon.centerZ() - central.z) + moon.radius() > params.systemRadius) continue;
                boolean valid = true;
                for (var other : result) {
                    var s = other.sphere;
                    if (Math.sqrt(squared(moon.centerX() - s.centerX(), moon.centerY() - s.centerY(), moon.centerZ() - s.centerZ()))
                            < moon.radius() + s.radius() + params.satelliteGap) { valid = false; break; }
                }
                if (valid) result.add(new Moon(planets.get(i).id, moon));
            }
        }
        return List.copyOf(result);
    }

    public record GuardianPost(int index, int x, int y, int z) { }

    /** Activation radius in blocks, including a rounding margin for the twelve surface-covering posts. */
    public static final int GUARDIAN_ACTIVATION_RANGE = 136;

    /**
     * Returns twelve cage sites covering the sun in three dimensions for current worlds.
     * The sites follow icosahedron vertices, rotated deterministically around Y. Whole-set
     * rotations retain coverage while avoiding planets and moons. Older layouts keep their
     * three original equatorial sites. Impossible packing fails explicitly, never leaving
     * a silently unguarded sector. Call only when guardian generation is enabled.
     *
     * @param system immutable system whose sun is defended
     * @return immutable cage positions, in block coordinates, cached per layout (at most 256 systems)
     */
    public List<GuardianPost> guardianPosts(System system) {
        int slot = (int) system.id() & 255;
        var cached = guardianCache.get(slot);
        if (cached != null && cached.systemId() == system.id()) return cached.posts();
        var posts = computeGuardianPosts(system);
        guardianCache.set(slot, new GuardianSites(system.id(), posts));
        return posts;
    }

    private List<GuardianPost> computeGuardianPosts(System system) {
        if (version < 5) return legacyGuardianPosts(system);
        var center = system.central();
        double phi = (1 + Math.sqrt(5)) / 2;
        double length = Math.sqrt(1 + phi * phi);
        double phase = unit(mix(center.id + 53)) * Math.PI * 2;
        for (int gap : new int[]{24, 16}) for (int rotation = 0; rotation < 64; rotation++) {
            double angle = phase + rotation * Math.PI * 2 / 64;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double radius = center.radius + gap;
            List<GuardianPost> posts = new ArrayList<>(12);
            for (int index = 0; index < 12; index++) {
                // Four sign combinations for each vertex group: (0,a,b), (a,b,0), (b,0,a).
                double a = (index % 2 == 0 ? -1 : 1) / length;
                double b = (index % 4 < 2 ? -phi : phi) / length;
                double dx = index / 4 == 0 ? 0 : index / 4 == 1 ? a : b;
                double dy = index / 4 == 0 ? a : index / 4 == 1 ? b : 0;
                double dz = index / 4 == 0 ? b : index / 4 == 1 ? 0 : a;
                int x = (int) Math.round(center.x + radius * (cos * dx - sin * dz));
                int y = (int) Math.round(center.y + radius * dy);
                int z = (int) Math.round(center.z + radius * (sin * dx + cos * dz));
                boolean clear = y - 1 >= MIN_Y && y + 6 <= MAX_Y;
                for (var body : system.bodies()) {
                    if (squared(x-body.x, y-body.y, z-body.z) < (body.radius+16.0)*(body.radius+16.0)) { clear = false; break; }
                }
                for (var moon : system.moons()) {
                    var m = moon.sphere();
                    if (squared(x-m.centerX(), y-m.centerY(), z-m.centerZ()) < (m.radius()+16.0)*(m.radius()+16.0)) { clear = false; break; }
                }
                if (!clear) break;
                posts.add(new GuardianPost(index, x, y, z));
            }
            if (posts.size() == 12) return List.copyOf(posts);
        }
        throw new IllegalArgumentException("Solar guardian cages cannot cover the sun without collisions; increase planet_gap or disable guardians");
    }

    private List<GuardianPost> legacyGuardianPosts(System system) {
        if (version < 4) return List.of();
        List<GuardianPost> posts = new ArrayList<>();
        var center = system.central();
        double radius = center.radius + 24;
        double phase = unit(mix(center.id + 53)) * Math.PI * 2;
        for (int index = 0; index < 3; index++) {
            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = phase + (index + attempt / 48.0) * Math.PI * 2 / 3;
                int x = (int) Math.round(center.x + radius * Math.cos(angle));
                int z = (int) Math.round(center.z + radius * Math.sin(angle));
                int y = (int) center.y;
                boolean clear = true;
                for (var body : system.bodies()) {
                    if (Math.sqrt(squared(x - body.x, y - body.y, z - body.z)) < body.radius + 16) { clear = false; break; }
                }
                for (var moon : system.moons()) {
                    var m = moon.sphere();
                    if (Math.sqrt(squared(x - m.centerX(), y - m.centerY(), z - m.centerZ())) < m.radius() + 16) { clear = false; break; }
                }
                if (clear) { posts.add(new GuardianPost(index, x, y, z)); break; }
            }
        }
        return List.copyOf(posts);
    }

    private static double squared(double x, double y, double z) { return x * x + y * y + z * z; }
    private static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
