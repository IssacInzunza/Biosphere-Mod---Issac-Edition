package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueba de la clausula de tapa plana ({@code close_open_top}) del bucle de
 * {@code SphereCarver.carve}.
 *
 * <p>Con la opcion apagada la clausula debe ser identica a la anterior. Con la
 * opcion activa, en el Y colocable mas alto (319) el cascaron ya cubre todo el
 * disco de la seccion si {@code centerY + radius <= 321}; solo queda hueco
 * central cuando {@code centerY + radius > 321}, y basta la condicion
 * {@code distanceSq <= radius^2} con el radio efectivo del cascaron.</p>
 *
 * <p>{@link #legacyClause} y {@link #cappedClause} son copias literales de las
 * dos versiones del bucle, sin depender de Minecraft, para que la prueba sea
 * pura y rapida.</p>
 */
class SphereTopCapTest {

    private static final int TOP_CAP_Y = 319;

    /** Predicado anterior al cambio. */
    private static boolean legacyClause(int radius, double horizontalDistSq, int y, int centerY) {
        double dy = y - centerY;
        double distanceSq = horizontalDistSq + dy * dy;
        double innerRadiusSq = (radius - SphereLayout.SHELL_THICKNESS) * (radius - SphereLayout.SHELL_THICKNESS);
        double outerRadiusSq = radius * radius;
        return distanceSq >= innerRadiusSq && distanceSq <= outerRadiusSq;
    }

    /** Predicado actual: copia literal de la condicion de SphereCarver.carve. */
    private static boolean cappedClause(int radius, double horizontalDistSq, int y, int centerY,
                                        boolean closeOpenTop) {
        double dy = y - centerY;
        double distanceSq = horizontalDistSq + dy * dy;
        double innerRadiusSq = (radius - SphereLayout.SHELL_THICKNESS) * (radius - SphereLayout.SHELL_THICKNESS);
        double outerRadiusSq = radius * radius;
        return distanceSq <= outerRadiusSq
                && (distanceSq >= innerRadiusSq || (closeOpenTop && y == TOP_CAP_Y));
    }

    @Test
    void disabledOptionIsExactNoOp() {
        int[] centerYs = {0, 62, 193, 194, 200, 250, 320, 400};
        int[] radiuses = {1, 2, 3, 4, 8, 16, 32, 64, 128, 192, 256, 300, 319, 320};
        for (int centerY : centerYs) {
            for (int radius : radiuses) {
                double outer = (double) radius * radius;
                for (int sample = 0; sample <= 32; sample++) {
                    double horizontal = outer * sample / 32.0;
                    for (int y = centerY - radius - 1; y <= centerY + radius + 1; y++) {
                        if (legacyClause(radius, horizontal, y, centerY)
                                != cappedClause(radius, horizontal, y, centerY, false)) {
                            fail("apagada difiere en centerY=" + centerY + " radius=" + radius
                                    + " y=" + y + " horizontalDistSq=" + horizontal);
                        }
                    }
                }
            }
        }
    }

    /** En el limite centerY + radius == 321 el cascaron ya cierra el disco entero. */
    @Test
    void shellAlreadyCoversWholeDiskAtBoundary() {
        int radius = 128;
        int centerY = 321 - radius;
        double dy = TOP_CAP_Y - centerY;
        int holes = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double horizontal = (double) x * x + (double) z * z;
                if (horizontal + dy * dy <= (double) radius * radius
                        && !legacyClause(radius, horizontal, TOP_CAP_Y, centerY)) {
                    holes++;
                }
            }
        }
        assertEquals(0, holes, "no debe quedar hueco en centerY + radius == 321");
    }

    /** Un bloque mas alla del limite si deja hueco, y solo entonces lo rellena la tapa. */
    @Test
    void justAboveBoundaryLeavesHoleAndCapFillsIt() {
        int radius = 128;
        int centerY = 322 - radius;
        assertFalse(legacyClause(radius, 0, TOP_CAP_Y, centerY),
                "debe haber hueco central en centerY + radius == 322");
        assertTrue(cappedClause(radius, 0, TOP_CAP_Y, centerY, true), "la tapa debe rellenar el hueco");
        assertFalse(cappedClause(radius, 0, TOP_CAP_Y, centerY, false), "apagada no debe rellenarlo");
    }
}
