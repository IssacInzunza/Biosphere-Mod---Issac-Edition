package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas nuevas sin cableado al motor; las pruebas anteriores conservan la ruta legacy. */
class SphereShellRulesTest {
    @Test
    void fourHemisphereCombinationsPartitionTheShell() {
        boolean[][] cases = {
                // superior, inferior, esperado debajo, ecuador, encima
                {true, true, true, true, true},
                {true, false, false, true, true},
                {false, true, true, false, false},
                {false, false, false, false, false}
        };
        for (var entry : cases) {
            assertEquals(entry[2], SphereLayout.shellHemisphereEnabled(61, 62, entry[0], entry[1]));
            assertEquals(entry[3], SphereLayout.shellHemisphereEnabled(62, 62, entry[0], entry[1]));
            assertEquals(entry[4], SphereLayout.shellHemisphereEnabled(63, 62, entry[0], entry[1]));
        }
    }

    @Test
    void eachSphereUsesItsOwnCenterWithoutAnAbsoluteHeightCutoff() {
        for (int center : new int[]{-64, 0, 55, 62, 127, 319, 400}) {
            assertTrue(SphereLayout.shellHemisphereEnabled(center - 1, center, false, true));
            assertFalse(SphereLayout.shellHemisphereEnabled(center, center, false, true));
            assertTrue(SphereLayout.shellHemisphereEnabled(center, center, true, false));
        }
        assertFalse(SphereLayout.shellHemisphereEnabled(127, 127.5, true, false));
        assertTrue(SphereLayout.shellHemisphereEnabled(128, 127.5, true, false));
        assertFalse(SphereLayout.shellHemisphereEnabled(Integer.MIN_VALUE, Integer.MAX_VALUE, true, false));
        assertTrue(SphereLayout.shellHemisphereEnabled(Integer.MAX_VALUE, Integer.MIN_VALUE, true, false));
    }

    @Test
    void bothEnabledPreserveLiteralShellAndCapBits() {
        for (int center : new int[]{-64, 62, 127, 300, 400}) for (int radius : new int[]{1, 2, 8, 128, 512}) {
            for (boolean cap : new boolean[]{false, true}) {
                var before = new BitSet();
                var after = new BitSet();
                int index = 0;
                for (int sample = 0; sample <= 32; sample++) for (int y = -64; y <= 319; y++) {
                    double horizontalDistSq = (double) radius * radius * sample / 32.0;
                    boolean legacy = literalShell(radius, horizontalDistSq, y, center, cap);
                    before.set(index, legacy);
                    after.set(index++, legacy && SphereLayout.shellHemisphereEnabled(y, center, true, true));
                }
                assertArrayEquals(before.toLongArray(), after.toLongArray(), "La seleccion completa debe ser un no-op exacto");
            }
        }
    }

    @Test
    void linerMatchesLiteralClassicBandWhenEnabledAndIsEmptyWhenDisabled() {
        for (int center : new int[]{-64, 62, 127, 319}) for (int radius : new int[]{1, 2, 3, 8, 128, 512, 46340}) {
            double shellInner = radius - SphereLayout.SHELL_THICKNESS;
            double linerInner = Math.max(0.0, shellInner - 3);
            for (int sample = 0; sample <= 32; sample++) for (int y = -64; y <= 319; y++) {
                double horizontalDistSq = (double) radius * radius * sample / 32.0;
                double dy = y - center;
                double distanceSq = horizontalDistSq + dy * dy;
                assertEquals(literalLiner(radius, horizontalDistSq, y, center),
                        SphereLayout.bedrockLinerContains(true, y, center, distanceSq, shellInner * shellInner, linerInner * linerInner));
                assertFalse(SphereLayout.bedrockLinerContains(false, y, center, distanceSq, shellInner * shellInner, linerInner * linerInner));
            }
        }
    }

    @Test
    void bedrockCanExistWithEitherOrBothGlassHemispheresDisabled() {
        for (boolean upper : new boolean[]{false, true}) for (boolean lower : new boolean[]{false, true}) {
            // Radio 8: el punto (x=4, y=center-1) esta dentro del forro, fuera del cascaron.
            assertEquals(lower, SphereLayout.shellHemisphereEnabled(61, 62, upper, lower));
            assertEquals(upper, SphereLayout.shellHemisphereEnabled(62, 62, upper, lower));
            assertTrue(SphereLayout.bedrockLinerContains(true, 61, 62, 17, 36, 9));
            assertTrue(SphereLayout.bedrockLinerContains(true, 62, 62, 16, 36, 9));
            assertFalse(SphereLayout.bedrockLinerContains(true, 63, 62, 17, 36, 9));
        }
    }

    @Test
    void linerKeepsThreeLayersAndItsIndependentCeiling() {
        for (int radius : new int[]{8, 16, 128, 512}) {
            double shellInnerSq = (radius - 2.0) * (radius - 2.0);
            double linerInnerSq = (radius - 5.0) * (radius - 5.0);
            int layers = 0;
            for (int x = 0; x <= radius; x++) {
                if (SphereLayout.bedrockLinerContains(true, 62, 62, (double) x * x, shellInnerSq, linerInnerSq)) layers++;
            }
            assertEquals(3, layers);
            assertTrue(SphereLayout.bedrockLinerContains(true, 62, 62, linerInnerSq, shellInnerSq, linerInnerSq));
            assertFalse(SphereLayout.bedrockLinerContains(true, 62, 62, shellInnerSq, shellInnerSq, linerInnerSq));
        }
        // El sol Solar tiene centro elevado, pero su forro puede conservar un techo inferior.
        assertTrue(SphereLayout.bedrockLinerContains(true, 63, 63, 17, 36, 9));
        assertFalse(SphereLayout.bedrockLinerContains(true, 64, 63, 17, 36, 9));
    }

    /** Copia literal de la condicion actual de SphereCarver.carve, sin Minecraft. */
    private static boolean literalShell(int radius, double horizontalDistSq, int y, int centerY, boolean closeOpenTop) {
        double dy = y - centerY;
        double distanceSq = horizontalDistSq + dy * dy;
        double innerRadiusSq = (radius - SphereLayout.SHELL_THICKNESS) * (radius - SphereLayout.SHELL_THICKNESS);
        double outerRadiusSq = radius * radius;
        return distanceSq <= outerRadiusSq
                && (distanceSq >= innerRadiusSq || (closeOpenTop && y == 319));
    }

    /** Copia de la banda clasica con bedrock_mode=SHELL y only_upper_hemisphere=false. */
    private static boolean literalLiner(int radius, double horizontalDistSq, int y, int centerY) {
        double dy = y - centerY;
        double distanceSq = horizontalDistSq + dy * dy;
        double innerRadiusSq = (radius - SphereLayout.SHELL_THICKNESS) * (radius - SphereLayout.SHELL_THICKNESS);
        double linerInnerRadius = radius - SphereLayout.SHELL_THICKNESS - 3;
        if (linerInnerRadius < 0.0) linerInnerRadius = 0.0;
        double linerInnerRadiusSq = linerInnerRadius * linerInnerRadius;
        return dy <= 0.0 && distanceSq >= linerInnerRadiusSq && distanceSq < innerRadiusSq;
    }
}
