package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pruebas del modo {@code bedrock_mode} del bucle de {@code SphereCarver.carve}.
 *
 * <p>Todas las condiciones son copias literales del carver, sin depender de
 * Minecraft, siguiendo el estilo de {@link SphereTopCapTest}.</p>
 *
 * <p>El punto central es la identidad con {@code floor}: la escritura nueva es
 * {@code escrituraAntigua || forro}, y con floor el forro es falso, asi que el
 * conjunto de bloques escritos por el bucle es exactamente el mismo. El modo
 * {@code none} vive en un barrido aparte que solo se ejecuta con ese valor.</p>
 */
class SphereBedrockModeTest {

    private static final double SHELL_THICKNESS = SphereLayout.SHELL_THICKNESS;
    private static final int LINER_THICKNESS = 3;
    private static final int FLOOR_BAND_TOP = 5;
    private static final int MIN_BUILD_HEIGHT = -64;
    private static final int MAX_BUILD_HEIGHT = 320;

    private enum Mode { FLOOR, NONE, SHELL }

    /** Escritura del bucle anterior al cambio: solo el cascaron. */
    private static boolean legacyWrite(int radius, double horizontalDistSq, int y, int centerY,
                                       boolean closeOpenTop, int topCapY,
                                       boolean onlyUpper, boolean replaceable, boolean water) {
        return shellClause(radius, horizontalDistSq, y, centerY, closeOpenTop, topCapY)
                && (!onlyUpper || replaceable)
                && !(onlyUpper && y < 55 && !water);
    }

    /** Clausula del cascaron (sin cambios). */
    private static boolean shellClause(int radius, double horizontalDistSq, int y, int centerY,
                                       boolean closeOpenTop, int topCapY) {
        double dy = y - centerY;
        double distanceSq = horizontalDistSq + dy * dy;
        double innerRadiusSq = (radius - SHELL_THICKNESS) * (radius - SHELL_THICKNESS);
        double outerRadiusSq = (double) radius * radius;
        return distanceSq <= outerRadiusSq
                && (distanceSq >= innerRadiusSq || (closeOpenTop && y == topCapY));
    }

    /** Rama nueva del forro (copia literal del {@code else if} del carver). */
    private static boolean linerWrite(Mode mode, boolean onlyUpper, int radius, double horizontalDistSq,
                                      int y, int centerY) {
        boolean bedrockLiner = mode == Mode.SHELL && !onlyUpper;
        if (!bedrockLiner) {
            return false;
        }
        double dy = y - centerY;
        double distanceSq = horizontalDistSq + dy * dy;
        double innerRadiusSq = (radius - SHELL_THICKNESS) * (radius - SHELL_THICKNESS);
        double linerInnerRadius = radius - SHELL_THICKNESS - LINER_THICKNESS;
        if (linerInnerRadius < 0.0) linerInnerRadius = 0.0;
        double linerInnerRadiusSq = linerInnerRadius * linerInnerRadius;
        return dy <= 0.0 && distanceSq >= linerInnerRadiusSq && distanceSq < innerRadiusSq;
    }

    /** Escritura del bucle nuevo: cascaron o forro. */
    private static boolean newWrite(Mode mode, int radius, double horizontalDistSq, int y, int centerY,
                                    boolean closeOpenTop, int topCapY,
                                    boolean onlyUpper, boolean replaceable, boolean water) {
        return legacyWrite(radius, horizontalDistSq, y, centerY, closeOpenTop, topCapY,
                onlyUpper, replaceable, water)
                || linerWrite(mode, onlyUpper, radius, horizontalDistSq, y, centerY);
    }

    /** Banda del barrido del modo none, tal como la calcula el carver. */
    private static int bottomBandTop() {
        return Math.min(MAX_BUILD_HEIGHT - 1, MIN_BUILD_HEIGHT + FLOOR_BAND_TOP);
    }

    private static boolean clearsFloor(Mode mode) {
        return mode == Mode.NONE;
    }

    /** Con floor la escritura nueva es identica a la anterior, para toda entrada. */
    @Test
    void floorIsExactNoOp() {
        int[] centerYs = {0, 55, 62, 200, 400};
        int[] radiuses = {1, 2, 3, 4, 5, 8, 16, 32, 64, 128, 256, 300};
        for (int centerY : centerYs) {
            for (int radius : radiuses) {
                double outer = (double) radius * radius;
                for (int sample = 0; sample <= 32; sample++) {
                    double horizontal = outer * sample / 32.0;
                    for (int y = centerY - radius - 1; y <= centerY + radius + 1; y++) {
                        for (boolean closeOpenTop : new boolean[]{false, true}) {
                            for (boolean onlyUpper : new boolean[]{false, true}) {
                                for (boolean replaceable : new boolean[]{false, true}) {
                                    for (boolean water : new boolean[]{false, true}) {
                                        boolean before = legacyWrite(radius, horizontal, y, centerY,
                                                closeOpenTop, 319, onlyUpper, replaceable, water);
                                        boolean after = newWrite(Mode.FLOOR, radius, horizontal, y, centerY,
                                                closeOpenTop, 319, onlyUpper, replaceable, water);
                                        if (before != after) {
                                            fail("floor difiere en centerY=" + centerY + " radius=" + radius
                                                    + " y=" + y + " horizontalDistSq=" + horizontal
                                                    + " closeOpenTop=" + closeOpenTop
                                                    + " onlyUpper=" + onlyUpper
                                                    + " replaceable=" + replaceable + " water=" + water);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Con floor no se ejecuta el barrido del modo none. */
    @Test
    void floorDoesNotSweepBottomBand() {
        assertFalse(clearsFloor(Mode.FLOOR));
        assertFalse(clearsFloor(Mode.SHELL));
        assertTrue(clearsFloor(Mode.NONE));
    }

    /** shell + only_upper_hemisphere es identico a floor. */
    @Test
    void shellWithUpperHemisphereIsFloor() {
        int[] radiuses = {3, 4, 8, 32, 128, 256};
        for (int radius : radiuses) {
            for (int y = -64; y <= 40; y++) {
                for (int sample = 0; sample <= 32; sample++) {
                    double horizontal = (double) radius * radius * sample / 32.0;
                    assertFalse(linerWrite(Mode.SHELL, true, radius, horizontal, y, 62),
                            "only_upper_hemisphere no debe forrar (radius=" + radius + " y=" + y + ")");
                }
            }
        }
    }

    /** El forro son exactamente tres capas pegadas por dentro al cascaron. */
    @Test
    void linerIsThreeLayersUnderTheShell() {
        int centerY = 62;
        for (int radius : new int[]{8, 16, 64, 128, 256}) {
            int layers = 0;
            for (int x = 0; x <= radius; x++) {
                if (linerWrite(Mode.SHELL, false, radius, (double) x * x, centerY, centerY)) {
                    layers++;
                }
            }
            assertEquals(LINER_THICKNESS, layers, "capas del forro con radius=" + radius);
        }
    }

    /** Bordes del forro: cascaron, limite interior, y == centerY y y == centerY + 1. */
    @Test
    void linerBoundaries() {
        int radius = 128;
        int centerY = 62;

        // El cascaron empieza en radius - 2 y nunca se toca.
        for (int x = radius - 2; x <= radius; x++) {
            assertFalse(linerWrite(Mode.SHELL, false, radius, (double) x * x, centerY, centerY),
                    "el cascaron no debe forrarse (x=" + x + ")");
        }

        // Ultima capa del forro justo por dentro del cascaron.
        assertTrue(linerWrite(Mode.SHELL, false, radius, (double) (radius - 3) * (radius - 3), centerY, centerY));

        // Limite interior exacto incluido, un bloque mas adentro excluido.
        assertTrue(linerWrite(Mode.SHELL, false, radius, (double) (radius - 5) * (radius - 5), centerY, centerY));
        assertFalse(linerWrite(Mode.SHELL, false, radius, (double) (radius - 6) * (radius - 6), centerY, centerY));

        // Mitad inferior: y == centerY si, y == centerY + 1 no. El punto radial
        // usado esta dentro de la banda del forro para que la unica diferencia
        // sea la Y.
        double inLinerBand = (double) (radius - 4) * (radius - 4);
        assertTrue(linerWrite(Mode.SHELL, false, radius, inLinerBand, centerY, centerY));
        assertFalse(linerWrite(Mode.SHELL, false, radius, inLinerBand, centerY + 1, centerY));
        assertTrue(linerWrite(Mode.SHELL, false, radius, inLinerBand, centerY - 1, centerY));

        // El nucleo de la esfera no es forro.
        assertFalse(linerWrite(Mode.SHELL, false, radius, 0.0, centerY, centerY));

        // Fuera de la esfera tampoco hay forro.
        assertFalse(linerWrite(Mode.SHELL, false, radius, (double) radius * radius, centerY, centerY));
    }

    /** Radios pequenos: el forro degenera, pero nunca hay excepciones ni capas negativas. */
    @Test
    void smallRadiiDegradeGracefully() {
        for (int radius = 1; radius <= 8; radius++) {
            int layers = 0;
            for (int x = 0; x <= radius + 1; x++) {
                if (linerWrite(Mode.SHELL, false, radius, (double) x * x, 62, 62)) {
                    layers++;
                }
            }
            assertTrue(layers >= 0 && layers <= LINER_THICKNESS,
                    "radio " + radius + " produce " + layers + " capas");
        }
    }

    /** Modo none: la banda barrida es minY..minY+5, y solo se sustituye bedrock. */
    @Test
    void noneSweepsOnlyTheBedrockBand() {
        assertEquals(-59, bottomBandTop());
        assertTrue(-64 >= MIN_BUILD_HEIGHT && -64 <= bottomBandTop());
        assertTrue(-59 <= bottomBandTop());
        assertFalse(-58 <= bottomBandTop(), "la banda no debe llegar a -58");
    }
}
