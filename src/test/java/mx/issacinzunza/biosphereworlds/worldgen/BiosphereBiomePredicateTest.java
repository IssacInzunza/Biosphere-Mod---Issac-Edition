package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba pura del predicado que decide si una posicion usa el bioma normal o
 * {@code minecraft:the_void}: {@link SphereLayout#isInside(double, double, double, double)}
 * con margen {@code -4}.
 *
 * <p>Es el mismo predicado que usan el wrapper antiguo y el mixin del biome source.
 * La equivalencia completa de biomas con una semilla concreta solo se puede probar
 * en el juego; aqui se fija la geometria y que los satelites no entren en el.</p>
 */
class BiosphereBiomePredicateTest {

    private static final int SPACING = 500;
    private static final int CENTER_Y = 62;
    private static final int RADIUS = 128;

    private static SphereLayout mainOnly() {
        return new SphereLayout(new SphereLayout.Params(SPACING, CENTER_Y, RADIUS, RADIUS));
    }

    private static SphereLayout withSatellites() {
        return new SphereLayout(new SphereLayout.Params(
                SPACING, CENTER_Y, RADIUS, RADIUS,
                true, 3, 8, 12, 25, 75, 16, 30, 55));
    }

    /**
     * Con margen {@code -4} el radio efectivo es {@code radius - 2 - (-4) = radius + 2}.
     * El limite es estricto: a {@code radius + 1.5} se sigue dentro; a
     * {@code radius + 2.5} ya se esta fuera.
     */
    @Test
    void elMargenMenosCuatroAmpliaElInteriorDosBloques() {
        SphereLayout layout = mainOnly();
        SphereLayout.Sphere sphere = layout.sphereAtCell(0, 0);
        double cx = sphere.centerX();
        double cy = sphere.centerY();
        double cz = sphere.centerZ();

        assertTrue(layout.isInside(cx, cy, cz, -4), "el centro debe estar dentro");
        assertTrue(layout.isInside(cx + sphere.radius() + 1.5, cy, cz, -4),
                "a radius + 1.5 el margen -4 todavia debe considerar interior");
        assertFalse(layout.isInside(cx + sphere.radius() + 2.5, cy, cz, -4),
                "a radius + 2.5 el margen -4 debe quedar fuera");
        assertFalse(layout.isInside(cx + sphere.radius() + 2.5, cy, cz, 0),
                "sin margen ese punto tambien esta fuera");
    }

    /**
     * Los satelites estan fuera de la esfera principal. El predicado del bioma solo
     * recorre esferas principales, asi que el centro de un satelite debe seguir siendo
     * {@code the_void}.
     */
    @Test
    void losSatelitesNoEntranEnElPredicadoDelBioma() {
        SphereLayout layout = withSatellites();
        boolean checked = false;

        for (int cellX = -20; cellX <= 20 && !checked; cellX++) {
            for (int cellZ = -20; cellZ <= 20 && !checked; cellZ++) {
                List<SphereLayout.Satellite> satellites = layout.satellitesAtCell(cellX, cellZ);
                if (satellites.isEmpty()) {
                    continue;
                }
                SphereLayout.Satellite satellite = satellites.get(0);
                assertTrue(layout.isInsideSatellite(satellite.centerX(), satellite.centerY(), satellite.centerZ()),
                        "el centro del satelite debe estar dentro de su propio volumen");
                assertFalse(layout.isInside(satellite.centerX(), satellite.centerY(), satellite.centerZ(), -4),
                        "el centro del satelite no debe entrar en el predicado de la esfera principal");
                checked = true;
            }
        }

        assertTrue(checked, "no se encontro ningun satelite en la muestra");
    }
}
