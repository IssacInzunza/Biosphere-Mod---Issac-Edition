package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ensambla la lista final de minerales de los satelites a partir de las listas ya
 * resueltas (sin Minecraft): vanilla, menas descubiertas por tag, inclusiones y
 * exclusiones.
 *
 * <p>Orden estable: primero los vanilla en su orden actual y despues los demas
 * por id. La exclusion gana sobre cualquier otra fuente. Es pura e inmutable.</p>
 */
public final class MineralCatalog {

    private MineralCatalog() {
    }

    public static List<SatelliteLayers.Mineral> assemble(List<SatelliteLayers.Mineral> vanilla,
                                                         Collection<String> tagOres,
                                                         Collection<String> included,
                                                         Collection<String> excluded,
                                                         int modWeight,
                                                         int modDensity) {
        Set<String> excludedIds = new HashSet<>(excluded);
        List<SatelliteLayers.Mineral> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (SatelliteLayers.Mineral mineral : vanilla) {
            if (!excludedIds.contains(mineral.blockId()) && seen.add(mineral.blockId())) {
                result.add(mineral);
            }
        }

        Set<String> modIds = new TreeSet<>();
        modIds.addAll(tagOres);
        modIds.addAll(included);
        for (String id : modIds) {
            if (!excludedIds.contains(id) && seen.add(id)) {
                result.add(new SatelliteLayers.Mineral(id, modWeight, modDensity));
            }
        }

        return List.copyOf(result);
    }
}
