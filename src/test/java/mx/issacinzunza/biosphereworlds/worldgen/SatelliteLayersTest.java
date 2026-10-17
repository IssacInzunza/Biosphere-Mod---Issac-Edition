package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas puras de la geometria de las capas del satelite y de la seleccion de
 * mineral. No necesitan Minecraft.
 */
class SatelliteLayersTest {

    private static final int[] RADII = {8, 10, 13, 20};

    @Test
    void lasCapasCoincidenConLaPropuesta() {
        assertLayers(8, 6, 5, 3);
        assertLayers(10, 8, 7, 5);
        assertLayers(13, 11, 10, 8);
        assertLayers(20, 18, 17, 14);
    }

    private static void assertLayers(int radius, int inner, int magmaOuter, int core) {
        SatelliteLayers.Layers layers = SatelliteLayers.layers(radius);
        assertEquals(inner, layers.innerRadius(), "inner de R=" + radius);
        assertEquals(magmaOuter, layers.magmaOuterRadius(), "magmaOuter de R=" + radius);
        assertEquals(core, layers.coreRadius(), "core de R=" + radius);
        assertTrue(layers.hasCore(), "R=" + radius + " debe tener nucleo");
        assertTrue(layers.magmaOuterRadius() > layers.coreRadius(), "debe haber magma");
        assertTrue(layers.innerRadius() > layers.magmaOuterRadius(), "debe haber piedra");
    }

    @Test
    void lasRegionesSonDisjuntasYEnOrden() {
        for (int radius : RADII) {
            SatelliteLayers.Layers layers = SatelliteLayers.layers(radius);
            for (double distanceSq = 0.0; distanceSq <= radius * radius + 4.0; distanceSq += 0.37) {
                SatelliteLayers.Region region = SatelliteLayers.regionAt(layers, distanceSq);
                if (distanceSq > square(layers.innerRadius())) {
                    assertEquals(SatelliteLayers.Region.SHELL, region, "fuera del vidrio");
                } else if (distanceSq <= square(layers.coreRadius())) {
                    assertEquals(SatelliteLayers.Region.CORE, region, "dentro del nucleo");
                } else if (distanceSq <= square(layers.magmaOuterRadius())) {
                    assertEquals(SatelliteLayers.Region.MAGMA, region, "banda de magma");
                } else {
                    assertEquals(SatelliteLayers.Region.STONE, region, "cuerpo de piedra");
                }
            }
        }
    }

    /** El cascaron debe conservar exactamente el umbral de la etapa 1. */
    @Test
    void elCascaronConservaElUmbralDeLaEtapa1() {
        for (int radius = 1; radius <= 40; radius++) {
            SatelliteLayers.Layers layers = SatelliteLayers.layers(radius);
            double innerSq = square(Math.max(0, radius - 2));
            for (double distanceSq = 0.0; distanceSq <= radius * radius + 2.0; distanceSq += 0.11) {
                boolean shell = distanceSq > innerSq;
                assertEquals(shell, SatelliteLayers.regionAt(layers, distanceSq) == SatelliteLayers.Region.SHELL,
                        "cascaron de R=" + radius + " en d^2=" + distanceSq);
            }
        }
    }

    /** Recorrido radial: nucleo -> magma -> piedra -> cascaron, sin saltos. */
    @Test
    void lasCapasAparecenEnOrdenAlAlejarseDelCentro() {
        for (int radius : RADII) {
            List<SatelliteLayers.Region> sequence = new ArrayList<>();
            for (int step = 0; step <= radius; step++) {
                double offset = step - 0.5;
                double distanceSq = offset * offset;
                SatelliteLayers.Region region = SatelliteLayers.regionAt(
                        SatelliteLayers.layers(radius), distanceSq);
                if (sequence.isEmpty() || sequence.get(sequence.size() - 1) != region) {
                    sequence.add(region);
                }
            }
            assertEquals(List.of(SatelliteLayers.Region.CORE, SatelliteLayers.Region.MAGMA,
                            SatelliteLayers.Region.STONE, SatelliteLayers.Region.SHELL),
                    sequence, "orden de capas de R=" + radius);
        }
    }

    /** La banda de magma debe ser una sola componente conexa por caras. */
    @Test
    void elMagmaEsContinuoYSinHuecos() {
        for (int radius : RADII) {
            SatelliteLayers.Layers layers = SatelliteLayers.layers(radius);
            double center = 0.5;
            Set<Long> magma = new HashSet<>();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        double dx = x + center;
                        double dy = y + center;
                        double dz = z + center;
                        if (SatelliteLayers.regionAt(layers, dx * dx + dy * dy + dz * dz)
                                == SatelliteLayers.Region.MAGMA) {
                            magma.add(key(x, y, z));
                        }
                    }
                }
            }
            assertFalse(magma.isEmpty(), "R=" + radius + " sin magma");
            assertEquals(magma.size(), connectedSize(magma), "magma partido en R=" + radius);
        }
    }

    @Test
    void losSatelitesPequenosNoFallan() {
        for (int radius = 1; radius <= 7; radius++) {
            SatelliteLayers.Layers layers = SatelliteLayers.layers(radius);
            assertTrue(layers.innerRadius() >= 0);
            if (radius <= 6) {
                assertFalse(layers.hasCore(), "R=" + radius + " debe quedarse sin nucleo");
            } else {
                assertTrue(layers.hasCore(), "R=" + radius + " ya cabe");
            }
            SatelliteLayers.Region center = SatelliteLayers.regionAt(layers, 0.0);
            assertEquals(layers.hasCore() ? SatelliteLayers.Region.CORE : SatelliteLayers.Region.STONE,
                    center, "centro pequeno de R=" + radius);
        }
    }

    @Test
    void laSeleccionDeMineralEsDeterminista() {
        for (long seed = 0; seed < 200; seed++) {
            SatelliteLayers.Mineral first = SatelliteLayers.selectMineral(
                    seed, SatelliteLayers.VANILLA_MINERALS);
            SatelliteLayers.Mineral second = SatelliteLayers.selectMineral(
                    seed, SatelliteLayers.VANILLA_MINERALS);
            assertEquals(first, second, "seed " + seed);
            assertTrue(SatelliteLayers.VANILLA_MINERALS.contains(first));
        }
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            distinct.add(SatelliteLayers.selectMineral(seed, SatelliteLayers.VANILLA_MINERALS).blockId());
        }
        assertTrue(distinct.size() >= 2, "la seleccion deberia usar mas de un mineral");
    }

    @Test
    void laSeleccionRespetaLosPesos() {
        int samples = 200_000;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (long seed = 0; seed < samples; seed++) {
            SatelliteLayers.Mineral mineral = SatelliteLayers.selectMineral(
                    seed, SatelliteLayers.VANILLA_MINERALS);
            counts.merge(mineral.blockId(), 1, Integer::sum);
        }
        for (SatelliteLayers.Mineral mineral : SatelliteLayers.VANILLA_MINERALS) {
            double expected = mineral.weight() / 100.0;
            double actual = counts.getOrDefault(mineral.blockId(), 0) / (double) samples;
            assertEquals(expected, actual, 0.01,
                    mineral.blockId() + " se desvia del peso esperado");
        }
    }

    @Test
    void laDensidadPorBloqueSeAproximaALaPedida() {
        int samples = 300_000;
        int coal = 0;
        int emerald = 0;
        for (int i = 0; i < samples; i++) {
            int x = i % 100;
            int y = (i / 100) % 100;
            int z = i / 10_000;
            if (SatelliteLayers.isOre(42L, x, y, z, 200)) {
                coal++;
            }
            if (SatelliteLayers.isOre(42L, x, y, z, 5)) {
                emerald++;
            }
        }
        assertEquals(0.200, coal / (double) samples, 0.01, "densidad de carbon");
        assertEquals(0.005, emerald / (double) samples, 0.002, "densidad de esmeralda");
        assertFalse(SatelliteLayers.isOre(1L, 0, 0, 0, 0));
        assertTrue(SatelliteLayers.isOre(1L, 0, 0, 0, 1000));
    }

    @Test
    void laListaVanillaEstaOrdenadaYEsInmutable() {
        for (int i = 1; i < SatelliteLayers.VANILLA_MINERALS.size(); i++) {
            String previous = SatelliteLayers.VANILLA_MINERALS.get(i - 1).blockId();
            String current = SatelliteLayers.VANILLA_MINERALS.get(i).blockId();
            assertTrue(previous.compareTo(current) <= 0, "lista no ordenada: " + previous + " > " + current);
        }
        assertThrows(UnsupportedOperationException.class,
                () -> SatelliteLayers.VANILLA_MINERALS.add(
                        new SatelliteLayers.Mineral("minecraft:coal_ore", 1, 1)));
        List<SatelliteLayers.Mineral> shuffled = new ArrayList<>(SatelliteLayers.VANILLA_MINERALS);
        java.util.Collections.reverse(shuffled);
        assertEquals(SatelliteLayers.VANILLA_MINERALS, SatelliteLayers.sorted(shuffled));
    }

    @Test
    void lasDensidadesPorDefectoCoincidenConLaPropuesta() {
        assertEquals(200, density("minecraft:coal_ore"));
        assertEquals(160, density("minecraft:copper_ore"));
        assertEquals(120, density("minecraft:iron_ore"));
        assertEquals(40, density("minecraft:gold_ore"));
        assertEquals(30, density("minecraft:redstone_ore"));
        assertEquals(20, density("minecraft:lapis_ore"));
        assertEquals(10, density("minecraft:diamond_ore"));
        assertEquals(5, density("minecraft:emerald_ore"));
    }

    private static int density(String blockId) {
        return SatelliteLayers.VANILLA_MINERALS.stream()
                .filter(mineral -> mineral.blockId().equals(blockId))
                .findFirst()
                .orElseThrow()
                .densityPermille();
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 128) << 42) ^ ((long) (y + 128) << 21) ^ (z + 128);
    }

    /** Tamano de la componente conexa por caras que contiene el primer bloque. */
    private static int connectedSize(Set<Long> blocks) {
        long start = blocks.iterator().next();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            int x = (int) (current >> 42) - 128;
            int y = (int) ((current >> 21) & 0x1FFFFF) - 128;
            int z = (int) (current & 0x1FFFFF) - 128;
            int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] offset : offsets) {
                long neighbor = key(x + offset[0], y + offset[1], z + offset[2]);
                if (blocks.contains(neighbor) && visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return visited.size();
    }
}
