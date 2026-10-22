package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Colocacion pura de los satelites de una esfera principal.
 *
 * <p>No toca {@code SphereConfig}, Minecraft ni ningun estado compartido: todo
 * sale de {@code (principal, vecinas, params, bounds)}. Por eso da el mismo
 * resultado en cualquier orden de generacion de chunks y se puede probar con
 * JUnit. La usan las celdas del modo grid; para {@code scattered} o {@code solar}
 * basta pasar otros limites y otras vecinas.</p>
 */
public final class SatellitePlacement {

    /** Limites verticales del mundo de la etapa 1. */
    public static final int WORLD_MIN_Y = -64;
    public static final int WORLD_MAX_Y = 319;

    /** Intentos por satelite antes de omitirlo sin error. */
    public static final int ATTEMPTS_PER_SATELLITE = 16;

    /** Separacion angular entre satelites consecutivos. */
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    /** Jitter maximo del angulo horizontal, en radianes (medio valor). */
    private static final double ANGLE_JITTER = 0.25;

    private SatellitePlacement() {
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
        return place(principal, neighbours, params, bounds, false);
    }

    /**
     * May lower an otherwise too-high satellite, preserving its radial distance,
     * size, seed and azimuth. The overload above preserves the previous placement.
     * The configured minimum elevation may be relaxed; all volume checks still apply.
     */
    public static List<SphereLayout.Satellite> place(SphereLayout.Sphere principal,
                                                     List<SphereLayout.Sphere> neighbours,
                                                     SphereLayout.Params params,
                                                     Bounds bounds,
                                                     boolean fitHeight) {
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
                SphereLayout.Satellite candidate = candidate(principal, params, bounds, index, attempt, fitHeight);
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
                                                    int attempt,
                                                    boolean fitHeight) {
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
        double centerY = principal.centerY() + distance * Math.sin(elevation);
        if (fitHeight && centerY + satelliteRadius > WORLD_MAX_Y) {
            // Set the center directly: the integer ceiling and radius sum exactly.
            double fittedY = (double) WORLD_MAX_Y - satelliteRadius;
            double vertical = fittedY - principal.centerY();
            if (vertical < 0.0) {
                return null; // No room even at the parent's equator; do not move it.
            }
            centerY = fittedY;
            horizontal = Math.sqrt(distance * distance - vertical * vertical);
        }
        double centerX = principal.centerX() + horizontal * Math.cos(angle);
        double centerZ = principal.centerZ() + horizontal * Math.sin(angle);

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
