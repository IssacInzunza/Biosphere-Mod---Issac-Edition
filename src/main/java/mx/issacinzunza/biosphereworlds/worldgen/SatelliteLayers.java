package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Geometria de las capas de un satelite y seleccion determinista de su mineral.
 *
 * <p>Es una clase pura: no lee configuracion ni toca Minecraft. La lista de
 * minerales se pasa como argumento; {@link #VANILLA_MINERALS} es solo la lista
 * por defecto de la etapa 2 y la etapa 3 podra sustituirla por otra lista
 * inmutable sin cambiar la seleccion.</p>
 */
public final class SatelliteLayers {

    /** Capas concentricas, de fuera hacia dentro. */
    public enum Region {
        SHELL,
        STONE,
        MAGMA,
        CORE
    }

    /** Radios de una capa, en bloques. Inmutable. */
    public record Layers(int innerRadius, int magmaOuterRadius, int coreRadius) {
        /** {@code true} si hay espacio para magma y nucleo. */
        public boolean hasCore() {
            return coreRadius >= 2;
        }
    }

    /**
     * Candidato de mineral: bloque, peso de seleccion y densidad de vetas.
     *
     * @param blockId         id de bloque, por ejemplo {@code minecraft:coal_ore}
     * @param weight          peso relativo de seleccion; debe ser {@code >= 0}
     * @param densityPermille bloques de mineral por cada 1000 del nucleo (0..1000)
     */
    public record Mineral(String blockId, int weight, int densityPermille) {
        public Mineral {
            Objects.requireNonNull(blockId, "blockId");
            if (blockId.isBlank()) {
                throw new IllegalArgumentException("blockId must not be blank");
            }
            if (weight < 0) {
                throw new IllegalArgumentException("weight must be >= 0, got " + weight);
            }
            if (densityPermille < 0 || densityPermille > 1000) {
                throw new IllegalArgumentException(
                        "densityPermille must be in 0..1000, got " + densityPermille);
            }
        }
    }

    /** Lista vanilla de la etapa 2, ordenada de forma estable por id de bloque. */
    public static final List<Mineral> VANILLA_MINERALS = sorted(List.of(
            new Mineral("minecraft:coal_ore", 25, 200),
            new Mineral("minecraft:copper_ore", 20, 160),
            new Mineral("minecraft:iron_ore", 20, 120),
            new Mineral("minecraft:gold_ore", 10, 40),
            new Mineral("minecraft:redstone_ore", 10, 30),
            new Mineral("minecraft:lapis_ore", 8, 20),
            new Mineral("minecraft:diamond_ore", 5, 10),
            new Mineral("minecraft:emerald_ore", 2, 5)
    ));

    private static final long SALT_MINERAL = 0x6A09E667F3BCC909L;
    private static final long SALT_ORE = 0xBB67AE8584CAA73BL;

    private SatelliteLayers() {
    }

    /**
     * Radios de las capas. Cascaron {@link SphereLayout#SHELL_THICKNESS}, un bloque
     * de piedra y una capa de magma de 2 a 4 bloques segun el tamano. Si el nucleo
     * resultante mide menos de 2 bloques de radio, {@link Layers#hasCore()} es
     * {@code false} y el satelite se queda en cascaron mas cuerpo de piedra.
     */
    public static Layers layers(int satelliteRadius) {
        int shell = (int) SphereLayout.SHELL_THICKNESS;
        int inner = Math.max(0, satelliteRadius - shell);
        int magmaThickness = Math.max(2, Math.min(4, satelliteRadius / 6));
        int magmaOuter = inner - 1;
        int core = magmaOuter - magmaThickness;
        return new Layers(inner, magmaOuter, core);
    }

    /** Region de un bloque a partir del cuadrado de su distancia al centro. */
    public static Region regionAt(Layers layers, double distanceSq) {
        if (distanceSq > square(layers.innerRadius())) {
            return Region.SHELL;
        }
        if (!layers.hasCore()) {
            return Region.STONE;
        }
        if (distanceSq <= square(layers.coreRadius())) {
            return Region.CORE;
        }
        if (distanceSq <= square(layers.magmaOuterRadius())) {
            return Region.MAGMA;
        }
        return Region.STONE;
    }

    /**
     * Elige un mineral de forma determinista. La lista debe ser inmutable y estar
     * ordenada de forma estable; {@link #sorted(List)} cumple ambas cosas.
     */
    public static Mineral selectMineral(long satelliteSeed, List<Mineral> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        long totalWeight = 0;
        for (Mineral candidate : candidates) {
            totalWeight += candidate.weight();
        }
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("total mineral weight must be > 0");
        }

        long roll = Math.floorMod(mix64(satelliteSeed ^ SALT_MINERAL), totalWeight);
        long cumulative = 0;
        for (Mineral candidate : candidates) {
            cumulative += candidate.weight();
            if (roll < cumulative) {
                return candidate;
            }
        }
        // Inalcanzable: roll < totalWeight y la suma acumulada termina en totalWeight.
        return candidates.get(candidates.size() - 1);
    }

    /**
     * {@code true} si el bloque es mineral. Usa un hash de 1000 cubos por bloque y
     * la densidad del candidato; con densidad 0 nunca devuelve mineral y con 1000
     * siempre.
     */
    public static boolean isOre(long satelliteSeed, int x, int y, int z, int densityPermille) {
        if (densityPermille <= 0) {
            return false;
        }
        if (densityPermille >= 1000) {
            return true;
        }
        long hash = mix64(satelliteSeed ^ SALT_ORE
                ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) y * 0xC2B2AE3D27D4EB4FL
                ^ (long) z * 0x165667B19E3779F9L);
        return Math.floorMod(hash, 1000L) < densityPermille;
    }

    /** Copia ordenada e inmutable de una lista de candidatos. */
    public static List<Mineral> sorted(List<Mineral> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(Mineral::blockId)
                        .thenComparingInt(Mineral::weight)
                        .thenComparingInt(Mineral::densityPermille))
                .toList();
    }

    /**
     * Cuenta los bloques de mineral del nucleo completo. Se usa en el log de
     * diagnostico; recibe el centro real del satelite para que el hash por bloque
     * coincida con el del carver.
     */
    public static int countOre(long satelliteSeed, Layers layers, int densityPermille,
                               double centerX, double centerY, double centerZ) {
        if (!layers.hasCore()) {
            return 0;
        }
        int radius = layers.coreRadius();
        double radiusSq = square(radius);
        int minX = (int) Math.floor(centerX - radius);
        int maxX = (int) Math.ceil(centerX + radius);
        int minY = (int) Math.floor(centerY - radius);
        int maxY = (int) Math.ceil(centerY + radius);
        int minZ = (int) Math.floor(centerZ - radius);
        int maxZ = (int) Math.ceil(centerZ + radius);

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            double dx = x - centerX;
            double dx2 = dx * dx;
            for (int y = minY; y <= maxY; y++) {
                double dy = y - centerY;
                double horizontal = dx2 + dy * dy;
                if (horizontal > radiusSq) {
                    continue;
                }
                for (int z = minZ; z <= maxZ; z++) {
                    double dz = z - centerZ;
                    if (horizontal + dz * dz > radiusSq) {
                        continue;
                    }
                    if (isOre(satelliteSeed, x, y, z, densityPermille)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }
}
