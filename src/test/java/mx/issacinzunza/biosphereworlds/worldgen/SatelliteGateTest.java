package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tabla de verdad de la puerta de los satelites. Es pura: no necesita Minecraft
 * ni NeoForge.
 */
class SatelliteGateTest {

    @Test
    void unMundoVanillaNuncaGeneraSatelitesAunqueLaOpcionEsteActiva() {
        assertFalse(SphereLayout.satellitesAllowed(false, true));
        assertFalse(SphereLayout.satellitesAllowed(false, false));
    }

    @Test
    void unMundoBiospheresSoloGeneraSatelitesConLaOpcionActiva() {
        assertTrue(SphereLayout.satellitesAllowed(true, true));
        assertFalse(SphereLayout.satellitesAllowed(true, false));
    }
}
