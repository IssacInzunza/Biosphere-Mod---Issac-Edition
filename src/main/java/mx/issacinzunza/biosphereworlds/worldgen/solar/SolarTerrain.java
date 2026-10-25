package mx.issacinzunza.biosphereworlds.worldgen.solar;

import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;

/** Pure Solar terrain rules and vertical reservations, expressed in global block coordinates. */
public final class SolarTerrain {
    /** Inclusive upper Y reserved for the complete central Stronghold. */
    public static final int STRONGHOLD_MAX_Y = 48;
    /** Inclusive lowest Y at which the central magma/ice profile can modify terrain. */
    public static final int CENTRAL_HAZARD_MIN_Y = 56;
    /** Inclusive ceiling of the sun's liner: terrain water level, not its raised geometric center. */
    public static final int CENTRAL_BEDROCK_MAX_Y = 63;
    /** Surface depth below the top block; inclusive, so the treatment spans six blocks. */
    private static final int CENTRAL_SURFACE_DEPTH = 5;
    /** Minimum interior clearance, in blocks, required by a complete Stronghold. */
    public static final int STRONGHOLD_INTERIOR_MARGIN = 5;
    /** Inner edge of the optional bedrock liner, measured from the outer sphere radius. */
    private static final int BEDROCK_INNER_MARGIN = 5;

    private SolarTerrain() { }

    /**
     * Keeps surface treatment above the complete Stronghold reservation.
     * @param y global block Y being considered
     * @param surfaceY highest solid surface in the current column
     * @return whether the block belongs to the central surface treatment
     */
    public static boolean centralSurface(int y, int surfaceY) {
        return y >= CENTRAL_HAZARD_MIN_Y && y <= surfaceY && y >= surfaceY - CENTRAL_SURFACE_DEPTH;
    }

    /**
     * @param y global block Y being considered
     * @param water whether the original block is water
     * @return whether central heat/cold may replace that water block
     */
    public static boolean centralFluid(int y, boolean water) {
        return y >= CENTRAL_HAZARD_MIN_Y && water;
    }

    /**
     * Historical block override for saved layouts 1–4; v5 uses the shared noise router.
     * @param body sphere containing the candidate block
     * @param x global block X
     * @param y global block Y
     * @param z global block Z
     * @param enabled saved edge-ocean switch
     * @param width radial ocean-band width percentage
     * @param depth floor depth below sea level, in blocks
     * @param seaLevel template water level, in global blocks
     * @return 0 to preserve the block, 1 for air, or 2 for water
     */
    public static int legacyOceanOverride(SolarLayout.Body body, int x, int y, int z,
                                    boolean enabled, int width, int depth, int seaLevel) {
        if (!enabled || width == 0 || y <= seaLevel - depth || !body.contains(x, y, z, 2)) return 0;
        double dy = seaLevel - body.y();
        double section = (body.radius() - 2.0) * (body.radius() - 2.0) - dy * dy;
        if (section <= 0) return 0;
        double start = Math.sqrt(section) * (1 - width / 100.0);
        double dx = x - body.x();
        double dz = z - body.z();
        if (dx * dx + dz * dz < start * start) return 0;
        return y <= seaLevel ? 2 : 1;
    }

    /**
     * Tests the lower inner band, capping the sun's liner at the terrain water level.
     * The sun's raised center reserves Stronghold space; it must not raise the liner.
     * Orbital planets keep their original center-relative ceiling.
     * @param body sphere containing the candidate block
     * @param x global block X
     * @param y global block Y
     * @param z global block Z
     * @param enabled saved liner switch; false returns an exact no-op
     * @return whether the block belongs to the lower inner bedrock band
     */
    public static boolean bedrockLiner(SolarLayout.Body body, int x, int y, int z, boolean enabled) {
        return bedrockLiner(body, x, y, z, enabled, false);
    }

    /** Con la mitad inferior abierta, mueve la banda a la superficie exterior y conserva su techo. */
    public static boolean bedrockLiner(SolarLayout.Body body, int x, int y, int z, boolean enabled, boolean exposed) {
        if (!enabled) return false;
        double ceiling = body.central() ? Math.min(body.y(), CENTRAL_BEDROCK_MAX_Y) : body.y();
        if (exposed) {
            double dx = x - body.x(), dy = y - body.y(), dz = z - body.z();
            return SphereLayout.exposedBedrockContains(true, y, ceiling, dx * dx + dy * dy + dz * dz, body.radius());
        }
        return y <= ceiling && body.contains(x, y, z, 2) && !body.contains(x, y, z, BEDROCK_INNER_MARGIN);
    }
}
