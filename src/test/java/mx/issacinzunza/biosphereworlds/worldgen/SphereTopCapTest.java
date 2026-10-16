package mx.issacinzunza.biosphereworlds.worldgen;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

/** Solo matematica: no arranca Minecraft ni consulta SphereConfig.
 * El recorrido de produccion apagado se conserva literalmente en SphereCarver;
 * aqui se contrasta su referencia pura con la logica historica.
 */
class SphereTopCapTest {
    @Test
    void apagadaCoincideBitABitConElRecorridoLiteralAnterior() {
        for (boolean upper : new boolean[]{false, true}) {
            for (int centerY : new int[]{40, 62, 280, 300, 319, 340, 700}) {
                SphereLayout layout = new SphereLayout(new SphereLayout.Params(96, centerY, 24, 48));
                for (int cx = -4; cx <= 4; cx++) {
                    for (int cz = -4; cz <= 4; cz++) {
                        assertEquals(mask(layout, cx, cz, upper, true),
                                mask(layout, cx, cz, upper, false),
                                "no-op: upper=" + upper + " centerY=" + centerY + " chunk=" + cx + "," + cz);
                    }
                }
            }
        }
    }

    private static BitSet mask(SphereLayout layout, int chunkX, int chunkZ, boolean upper, boolean legacy) {
        BitSet result = new BitSet();
        int chunkStartX = chunkX * 16;
        int chunkStartZ = chunkZ * 16;
        int chunkEndX = chunkStartX + 15;
        int chunkEndZ = chunkStartZ + 15;
        int centerY = layout.params().centerY();
        for (int gx = layout.firstCellIndex(chunkStartX); gx <= layout.lastCellIndex(chunkEndX); gx++) {
            for (int gz = layout.firstCellIndex(chunkStartZ); gz <= layout.lastCellIndex(chunkEndZ); gz++) {
                SphereLayout.Sphere sphere = layout.sphereAtCell(gx, gz);
                int radius = sphere.radius();
                if (upper) radius--;
                int minY = Math.max(-64, centerY - radius - 1);
                int maxY = legacy ? Math.min(320, centerY + radius + 1)
                        : layout.shellMaxY(320, SphereLayout.shellRadius(sphere, upper), false);
                double innerRadiusSq = (radius - 2.0) * (radius - 2.0);
                double outerRadiusSq = radius * radius;
                for (int x = chunkStartX; x <= chunkEndX; x++) {
                    double dx = x - sphere.centerX();
                    double dx2 = dx * dx;
                    if (dx2 > outerRadiusSq) continue;
                    for (int z = chunkStartZ; z <= chunkEndZ; z++) {
                        double dz = z - sphere.centerZ();
                        double dz2 = dz * dz;
                        double horizontalDistSq = dx2 + dz2;
                        if (horizontalDistSq > outerRadiusSq) continue;
                        for (int y = minY; y <= maxY; y++) {
                            double dy = y - centerY;
                            double distanceSq = horizontalDistSq + dy * dy;
                            boolean geometry = legacy
                                    ? distanceSq >= innerRadiusSq && distanceSq <= outerRadiusSq
                                    : layout.isTopCap(sphere, horizontalDistSq, y, upper, false)
                                        || layout.isShell(radius, horizontalDistSq, y);
                            // Tres escenarios independientes: aire, agua y solido.
                            for (int state = 0; state < 3; state++) {
                                boolean replace = !upper || (state != 2 && (y >= 55 || state == 1));
                                if (geometry && replace) {
                                    int index = (((x - chunkStartX) * 16 + z - chunkStartZ) * 385 + y + 64) * 3 + state;
                                    result.set(index);
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    @Test
    void discoCompletoSinHuecosNiSobrantesEnAmbosModosYEntreChunks() {
        for (boolean upper : new boolean[]{false, true}) {
            for (int[] config : new int[][]{{62, 260}, {250, 80}, {300, 40}, {319, 40}, {340, 40}}) {
                int cy = config[0];
                int nominalRadius = config[1];
                SphereLayout layout = new SphereLayout(new SphereLayout.Params(640, cy, nominalRadius, nominalRadius));
                for (int cell : new int[]{-1, 0, 1}) {
                    SphereLayout.Sphere sphere = layout.sphereAtCell(cell, -cell);
                    int r = nominalRadius - (upper ? 1 : 0);
                    assertEquals(r, SphereLayout.shellRadius(sphere, upper));
                    int lastChunk = Math.floorDiv((int) sphere.centerX() + r, 16);
                    int lastZChunk = Math.floorDiv((int) sphere.centerZ() + r, 16);
                    int ring = 0;
                    int interior = 0;
                    for (int cx = Math.floorDiv((int) sphere.centerX() - r, 16); cx <= lastChunk; cx++) {
                        for (int cz = Math.floorDiv((int) sphere.centerZ() - r, 16); cz <= lastZChunk; cz++) {
                            for (int x = cx * 16; x < cx * 16 + 16; x++) {
                                for (int z = cz * 16; z < cz * 16 + 16; z++) {
                                    double dx = x - sphere.centerX();
                                    double dz = z - sphere.centerZ();
                                    double h = dx * dx + dz * dz;
                                    double d = h + (319.0 - cy) * (319.0 - cy);
                                    boolean disk = d <= (double) r * r;
                                    boolean cap = layout.isTopCap(sphere, h, 319, upper, true);
                                    assertEquals(disk, cap, "disco incompleto o sobrante");
                                    boolean shell = layout.isShell(r, h, 319);
                                    assertEquals(disk, cap || shell, "hueco entre disco y anillo");
                                    if (shell) { ring++; assertTrue(cap, "anillo sin solape"); }
                                    if (disk && d < (r - 2.0) * (r - 2.0)) interior++;
                                    assertFalse(layout.isTopCap(sphere, h, 318, upper, true));
                                    assertFalse(layout.isTopCap(sphere, h, 320, upper, true));
                                }
                            }
                        }
                    }
                    assertTrue(ring > 0, "debe ejercitar el empalme");
                    // El primer caso apenas rebasa el limite y puede estar ya cerrado por el cascaron.
                    if (cy != 62) assertTrue(interior > 0, "debe rellenar una abertura real");
                    assertTrue(layout.shellMaxY(320, r, true) <= 319);
                    assertTrue(layout.shellMaxY(256, r, true) <= 255);
                }
            }
        }
    }

    @Test
    void noHayTapaSiNoSuperaElLimiteONoIntersecaElPlano() {
        for (boolean upper : new boolean[]{false, true}) {
            for (int cy : new int[]{62, 279, 280, 400}) {
                SphereLayout layout = new SphereLayout(new SphereLayout.Params(500, cy, 40, 40));
                SphereLayout.Sphere sphere = layout.sphereAtCell(0, 0);
                for (int x = -41; x <= 41; x++) {
                    for (int z = -41; z <= 41; z++) {
                        assertFalse(layout.isTopCap(sphere, x * x + z * z, 319, upper, true));
                    }
                }
            }
        }
    }

    @Test
    void agujerosGrandesSiguenAbiertosApagadaEnAmbosModos() {
        for (boolean upper : new boolean[]{false, true}) {
            for (int nominalRadius : new int[]{300, 512}) {
                SphereLayout layout = new SphereLayout(new SphereLayout.Params(2000, 62, nominalRadius, nominalRadius));
                SphereLayout.Sphere sphere = layout.sphereAtCell(-1, -1);
                int radius = nominalRadius - (upper ? 1 : 0);
                int emptyBlocks = 0;
                // Cuadricula de chunks alrededor de (-2000,-2000), en Y=319.
                for (int cx = -158; cx <= -92; cx++) {
                    for (int cz = -158; cz <= -92; cz++) {
                        for (int x = cx * 16; x < cx * 16 + 16; x++) {
                            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                                double dx = x - sphere.centerX();
                                double dz = z - sphere.centerZ();
                                double h = dx * dx + dz * dz;
                                double oldDistanceSq = h + 257.0 * 257.0;
                                double oldInnerSq = (radius - 2.0) * (radius - 2.0);
                                if (oldDistanceSq < oldInnerSq) {
                                    assertFalse(layout.isShell(radius, h, 319));
                                    assertFalse(layout.isTopCap(sphere, h, 319, upper, false));
                                    assertTrue(layout.isTopCap(sphere, h, 319, upper, true));
                                    emptyBlocks++;
                                }
                            }
                        }
                    }
                }
                assertTrue(emptyBlocks > 60_000, "debe conservar una abertura amplia, no un hueco de un bloque");
            }
        }
    }
}
