package mx.issacinzunza.biosphereworlds.worldgen.solar;

/** Pure terrain decisions; zero means preserve the original block exactly. */
public final class SolarTerrain {
    private SolarTerrain() { }
    /** Keep the complete stronghold envelope (max Y=48) below the altered surface. */
    public static boolean centralSurface(int y, int surfaceY) { return y >= 56 && y <= surfaceY && y >= surfaceY - 5; }
    public static boolean centralFluid(int y, boolean water) { return y >= 56 && water; }
    public static int oceanOverride(SolarLayout.Body body, int x, int y, int z,
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
    public static boolean bedrockLiner(SolarLayout.Body body, int x, int y, int z, boolean enabled) {
        return enabled && y <= body.y() && body.contains(x, y, z, 2) && !body.contains(x, y, z, 5);
    }
}
