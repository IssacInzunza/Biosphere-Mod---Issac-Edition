package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas puras del ensamblado de candidatos y de la seleccion ponderada.
 */
class MineralCatalogTest {

    @Test
    void nonemptyZeroWeightCatalogReproducesDeferredSelectionFailure() {
        var excluded = SatelliteLayers.VANILLA_MINERALS.stream().map(SatelliteLayers.Mineral::blockId).toList();
        var minerals = MineralCatalog.assemble(SatelliteLayers.VANILLA_MINERALS,
                Set.of(), Set.of("minecraft:stone"), excluded, 0, 20);
        assertEquals(List.of(new SatelliteLayers.Mineral("minecraft:stone", 0, 20)), minerals);
        var error = assertThrows(IllegalArgumentException.class, () -> SatelliteLayers.selectMineral(0, minerals));
        assertEquals("total mineral weight must be > 0", error.getMessage());
    }

    private static final int MOD_WEIGHT = 3;
    private static final int MOD_DENSITY = 20;

    @Test
    void sinModsNiListasElResultadoEsLaListaVanillaExacta() {
        List<SatelliteLayers.Mineral> result = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS,
                Set.of(), Set.of(), Set.of(), MOD_WEIGHT, MOD_DENSITY);
        assertEquals(SatelliteLayers.VANILLA_MINERALS, result);
        for (int i = 0; i < result.size(); i++) {
            assertEquals(SatelliteLayers.VANILLA_MINERALS.get(i), result.get(i),
                    "orden y valores deben ser los de la etapa 2");
        }
    }

    @Test
    void elDescubrimientoPorTagNoCambiaLaListaVanilla() {
        Set<String> discovered = new HashSet<>();
        for (SatelliteLayers.Mineral mineral : SatelliteLayers.VANILLA_MINERALS) {
            discovered.add(mineral.blockId());
        }
        Set<String> defaultExclusions = Set.of(
                "minecraft:ancient_debris",
                "minecraft:nether_quartz_ore",
                "minecraft:nether_gold_ore",
                "minecraft:deepslate_coal_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:deepslate_redstone_ore");
        List<SatelliteLayers.Mineral> result = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS, discovered, Set.of(), defaultExclusions,
                MOD_WEIGHT, MOD_DENSITY);
        assertEquals(SatelliteLayers.VANILLA_MINERALS, result,
                "sin mods, el tag y las exclusiones por defecto no deben cambiar nada");
    }

    @Test
    void laExclusionQuitaUnVanilla() {
        List<SatelliteLayers.Mineral> result = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS,
                Set.of(), Set.of(), Set.of("minecraft:coal_ore"), MOD_WEIGHT, MOD_DENSITY);
        assertFalse(result.stream().anyMatch(mineral -> mineral.blockId().equals("minecraft:coal_ore")));
        assertEquals(SatelliteLayers.VANILLA_MINERALS.size() - 1, result.size());
    }

    @Test
    void laInclusionSeAnadeDespuesDeLosVanillaYOrdenada() {
        List<SatelliteLayers.Mineral> result = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS,
                Set.of(), Set.of("zmod:ore", "amod:ore"), Set.of(), MOD_WEIGHT, MOD_DENSITY);
        int vanillaSize = SatelliteLayers.VANILLA_MINERALS.size();
        assertEquals(vanillaSize + 2, result.size());
        assertEquals("amod:ore", result.get(vanillaSize).blockId());
        assertEquals("zmod:ore", result.get(vanillaSize + 1).blockId());
        assertEquals(MOD_WEIGHT, result.get(vanillaSize).weight());
        assertEquals(MOD_DENSITY, result.get(vanillaSize).densityPermille());
    }

    @Test
    void laExclusionGanaSobreLaInclusionYLosDuplicadosSeUnen() {
        List<SatelliteLayers.Mineral> result = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS,
                Set.of("mod:tag_ore", "mod:shared"),
                Set.of("mod:shared", "mod:only_inclusion"),
                Set.of("mod:shared"), MOD_WEIGHT, MOD_DENSITY);
        assertEquals(0, result.stream().filter(mineral -> mineral.blockId().equals("mod:shared")).count());
        assertTrue(result.stream().anyMatch(mineral -> mineral.blockId().equals("mod:tag_ore")));
        assertTrue(result.stream().anyMatch(mineral -> mineral.blockId().equals("mod:only_inclusion")));
    }

    @Test
    void excluirTodoDejaLaListaVacia() {
        Set<String> all = new HashSet<>();
        for (SatelliteLayers.Mineral mineral : SatelliteLayers.VANILLA_MINERALS) {
            all.add(mineral.blockId());
        }
        List<SatelliteLayers.Mineral> result = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS, Set.of(), Set.of(), all, MOD_WEIGHT, MOD_DENSITY);
        assertTrue(result.isEmpty());
    }

    /**
     * La etapa 2 elegia con la lista vanilla (peso total 100). Esta copia literal
     * debe coincidir bit a bit con la implementacion nueva.
     */
    @Test
    void conLaListaVanillaLaSeleccionCoincideConLaEtapa2() {
        for (long seed = 0; seed < 500_000; seed++) {
            assertEquals(legacySelect(seed, SatelliteLayers.VANILLA_MINERALS),
                    SatelliteLayers.selectMineral(seed, SatelliteLayers.VANILLA_MINERALS),
                    "seed " + seed);
        }
    }

    /** El modulo debe ser el peso total de la lista, no 100. */
    @Test
    void elModuloEsElPesoTotalDeLaLista() {
        List<SatelliteLayers.Mineral> custom = List.of(
                new SatelliteLayers.Mineral("test:a", 3, 100),
                new SatelliteLayers.Mineral("test:b", 7, 100));
        int a = 0;
        int b = 0;
        for (long seed = 0; seed < 200_000; seed++) {
            SatelliteLayers.Mineral mineral = SatelliteLayers.selectMineral(seed, custom);
            assertEquals(legacySelect(seed, custom), mineral, "seed " + seed);
            if (mineral.blockId().equals("test:a")) {
                a++;
            } else {
                b++;
            }
        }
        assertEquals(0.30, a / 200_000.0, 0.01, "peso de test:a");
        assertEquals(0.70, b / 200_000.0, 0.01, "peso de test:b");
    }

    /** Copia literal de la logica de la etapa 2, con modulo del peso total. */
    private static SatelliteLayers.Mineral legacySelect(long seed, List<SatelliteLayers.Mineral> candidates) {
        long total = 0;
        for (SatelliteLayers.Mineral candidate : candidates) {
            total += candidate.weight();
        }
        long roll = Math.floorMod(mix64(seed ^ 0x6A09E667F3BCC909L), total);
        long cumulative = 0;
        for (SatelliteLayers.Mineral candidate : candidates) {
            cumulative += candidate.weight();
            if (roll < cumulative) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }
}
