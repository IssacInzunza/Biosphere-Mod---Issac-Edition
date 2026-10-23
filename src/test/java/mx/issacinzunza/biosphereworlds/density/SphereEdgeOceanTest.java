package mx.issacinzunza.biosphereworlds.density;

import mx.issacinzunza.biosphereworlds.density.SphereEdgeOcean.Mode;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la matematica del oceano exterior.
 *
 * <p>{@link SphereEdgeOcean} no depende de Minecraft ni de {@code SphereConfig}, asi
 * que se puede fijar bit a bit con JUnit. El envoltorio
 * {@code SphereEdgeOceanFunction} solo delega en estas mismas funciones, de modo que
 * lo probado aqui es exactamente lo que ejecutan {@code compute} y
 * {@code fillArray}.</p>
 *
 * <p>Nota: el no-op de {@code compute}/{@code fillArray} con la opcion desactivada es
 * un {@code return} del valor de la funcion envuelta; su verificacion bit a bit vive
 * aqui como identidad exacta de {@link SphereEdgeOcean#transform} con
 * {@code enabled = false}.</p>
 */
class SphereEdgeOceanTest {

    private record Config(String name, int spacing, int centerY, int minRadius, int maxRadius) {
    }

    private static final List<Config> CONFIGS = List.of(
            new Config("default", 500, 62, 128, 128),
            new Config("rango", 500, 62, 64, 128),
            new Config("densa", 100, 40, 48, 96),
            // Radio patologico del hash: no debe reventar ni cambiar nada.
            new Config("min>max", 500, 62, 200, 100)
    );

    private static final int WIDTH_PERCENT = 20;
    private static final int DEPTH = 20;

    private static SphereLayout layout(Config config) {
        return new SphereLayout(new SphereLayout.Params(config.spacing(), config.centerY(),
                config.minRadius(), config.maxRadius()));
    }

    // ------------------------------------------------------------------
    // No-op exacto
    // ------------------------------------------------------------------

    @Test
    void sharedMathMatchesLiteralPreviousImplementationBitForBit() {
        Random random = new Random(19102026L);
        for (Config config : CONFIGS) {
            var layout = layout(config);
            for (boolean enabled : new boolean[]{false, true}) for (int width : new int[]{0, 1, 20, 100}) {
                for (int i = 0; i < 5000; i++) {
                    int x = random.nextInt(4 * config.spacing()) - 2 * config.spacing();
                    int z = random.nextInt(4 * config.spacing()) - 2 * config.spacing();
                    int y = random.nextInt(384) - 64;
                    int depth = random.nextInt(101);
                    double input = random.nextDouble() * 8 - 4;
                    for (Mode mode : Mode.values()) {
                        double expected = SphereEdgeOceanLiteral.transform(
                                mode == Mode.FLOOR ? SphereEdgeOceanLiteral.Mode.FLOOR : SphereEdgeOceanLiteral.Mode.CLIMATE,
                                enabled, width, depth, layout, x, y, z, input);
                        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(
                                SphereEdgeOcean.transform(mode, enabled, width, depth, layout, x, y, z, input)));
                    }
                }
            }
        }
    }

    @Test
    void desactivadaEsNoOpExactoBitABit() {
        Random random = new Random(20240202L);
        double[] values = {0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 1.0e-9, -1.0e-9,
                Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Config config : CONFIGS) {
            SphereLayout layout = layout(config);
            for (Mode mode : Mode.values()) {
                for (double value : values) {
                    assertEquals(Double.doubleToLongBits(value),
                            Double.doubleToLongBits(SphereEdgeOcean.transform(mode, false,
                                    WIDTH_PERCENT, DEPTH, layout, 0, config.centerY(), 0, value)),
                            () -> config.name() + " " + mode + " desactivada altero " + value);
                }
                for (int i = 0; i < 5_000; i++) {
                    int x = random.nextInt(2 * config.spacing()) - config.spacing();
                    int z = random.nextInt(2 * config.spacing()) - config.spacing();
                    int y = random.nextInt(400) - 64;
                    double value = random.nextDouble() * 4 - 2;
                    assertEquals(Double.doubleToLongBits(value),
                            Double.doubleToLongBits(SphereEdgeOcean.transform(mode, false,
                                    WIDTH_PERCENT, DEPTH, layout, x, y, z, value)),
                            () -> config.name() + " " + mode + " desactivada altero un valor aleatorio");
                }
            }
        }
    }

    @Test
    void conAnchoCeroLaBandaNoExiste() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        Random random = new Random(7L);
        for (Mode mode : Mode.values()) {
            for (int i = 0; i < 5_000; i++) {
                int x = random.nextInt(400) - 200;
                int z = random.nextInt(400) - 200;
                int y = random.nextInt(400) - 64;
                double value = random.nextDouble() * 4 - 2;
                assertEquals(value, SphereEdgeOcean.transform(mode, true, 0, DEPTH, layout, x, y, z, value),
                        () -> config.name() + " ancho 0 altero el valor");
            }
        }
    }

    // ------------------------------------------------------------------
    // Peso de la banda
    // ------------------------------------------------------------------

    @Test
    void elPesoEsCeroDentroDeLaBandaYFueraDeLaEsfera() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        double sInner = 1.0 - WIDTH_PERCENT / 100.0;
        int radius = layout.sphereAtCell(0, 0).radius();

        // Interior: desde el centro hasta el limite interior de la banda.
        for (int x = 0; x <= (int) Math.floor(sInner * radius); x++) {
            final int probeX = x;
            assertEquals(0.0, SphereEdgeOcean.weight(layout, x, config.centerY(), 0, WIDTH_PERCENT / 100.0),
                    () -> "peso distinto de 0 dentro de la banda en x=" + probeX);
        }
        // Fuera de la esfera: el hueco exterior no se toca.
        for (int x = radius; x <= radius + 40; x++) {
            final int probeX = x;
            assertEquals(0.0, SphereEdgeOcean.weight(layout, x, config.centerY(), 0, WIDTH_PERCENT / 100.0),
                    () -> "peso distinto de 0 fuera de la esfera en x=" + probeX);
        }
        // Justo en el centro de la celda vecina (muy lejos del centro de esta esfera).
        assertEquals(0.0, SphereEdgeOcean.weight(layout, config.spacing(), config.centerY(), 0,
                WIDTH_PERCENT / 100.0), "el hueco entre esferas debe quedar intacto");
    }

    @Test
    void elPesoLlegaAUnoContraElVidrio() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        int radius = layout.sphereAtCell(0, 0).radius();
        int sEdge = (int) Math.ceil((radius - SphereLayout.SHELL_THICKNESS));
        for (int x = sEdge; x <= radius - 1; x++) {
            final int probeX = x;
            assertEquals(1.0, SphereEdgeOcean.weight(layout, x, config.centerY(), 0, WIDTH_PERCENT / 100.0),
                    () -> "peso distinto de 1 contra el vidrio en x=" + probeX);
        }
    }

    @Test
    void elPesoCreceDeFormaMonotonaHaciaElBordeYNoTieneEscalon() {
        for (Config config : CONFIGS) {
            SphereLayout layout = layout(config);
            int radius = layout.sphereAtCell(0, 0).radius();
            if (radius <= 2) {
                continue;
            }
            double widthFraction = WIDTH_PERCENT / 100.0;
            // El barrido va del borde hacia el centro, asi que el peso no puede subir.
            double previousWeight = Double.POSITIVE_INFINITY;
            int previousX = Integer.MIN_VALUE;
            double previousOutput = Double.NaN;
            // Cota del salto de peso entre dos bloques contiguos: el smoothstep tiene
            // pendiente maxima 1.5 y la rampa mide widthFraction * radius en s.
            double maxJump = 3.0 / (widthFraction * radius);
            int samples = 4 * radius;
            for (int i = 0; i <= samples; i++) {
                double position = radius - 1.0 - (radius - 1.0) * i / samples;
                int x = (int) Math.round(position);
                double weight = SphereEdgeOcean.weight(layout, x, config.centerY(), 0, widthFraction);
                assertTrue(weight >= 0.0 && weight <= 1.0,
                        () -> config.name() + " peso fuera de [0,1]: " + weight);
                final double lastWeight = previousWeight;
                assertTrue(weight <= previousWeight + 1.0e-12,
                        () -> config.name() + " el peso subio al acercarse al centro: " + weight + " > " + lastWeight);
                previousWeight = weight;

                // El efecto sobre el terreno tampoco puede tener escalon entre bloques
                // contiguos: es la comprobacion de "sin escalon visible".
                double output = SphereEdgeOcean.transform(Mode.FLOOR, true, WIDTH_PERCENT, DEPTH,
                        layout, x, config.centerY() + 4, 0, 0.5);
                if (!Double.isNaN(previousOutput) && x != previousX) {
                    final double lastOutput = previousOutput;
                    final int step = Math.abs(x - previousX);
                    final int probeX = x;
                    assertTrue(Math.abs(output - previousOutput) <= maxJump * step,
                            () -> config.name() + " escalon de " + Math.abs(output - lastOutput)
                                    + " entre bloques contiguos en x=" + probeX);
                }
                previousX = x;
                previousOutput = output;
            }
        }
    }

    // ------------------------------------------------------------------
    // Modo suelo
    // ------------------------------------------------------------------

    @Test
    void elSueloQuedaPorDebajoDelNivelDelMarCercaDelBorde() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        int radius = layout.sphereAtCell(0, 0).radius();
        int floorY = SphereEdgeOcean.SEA_LEVEL - DEPTH;
        assertTrue(floorY < SphereEdgeOcean.SEA_LEVEL, "el suelo debe quedar bajo el nivel del mar");

        // Contra el vidrio el peso es 1, asi que el suelo es exactamente floorY. Se elige
        // una x por cada y para que el punto siga cayendo dentro de la banda (a distancia
        // 3D menor que el radio) y no fuera de la esfera.
        double solidTerrain = 0.5;
        for (int y = SphereEdgeOcean.SEA_LEVEL; y > floorY; y--) {
            final int probeY = y;
            int dy = y - config.centerY();
            int channel = (int) Math.ceil(Math.sqrt(Math.max(0.0,
                    (radius - 1.0) * (radius - 1.0) - (double) dy * dy)));
            assertEquals(1.0, SphereEdgeOcean.weight(layout, channel, y, 0, WIDTH_PERCENT / 100.0),
                    () -> "el punto de prueba no cayo contra el vidrio en y=" + probeY);
            double value = SphereEdgeOcean.transform(Mode.FLOOR, true, WIDTH_PERCENT, DEPTH,
                    layout, channel, y, 0, solidTerrain);
            assertTrue(value <= 0.0,
                    () -> "a y=" + probeY + " el terreno sigue solido o neutro: " + value);
        }
        assertTrue(SphereEdgeOcean.transform(Mode.FLOOR, true, WIDTH_PERCENT, DEPTH,
                        layout, radius - 1, floorY - 1, 0, solidTerrain) > 0.0,
                "por debajo del suelo el terreno debe seguir solido");
    }

    /** Un terreno ya mas bajo que el suelo no se rellena: el perfil solo recorta. */
    @Test
    void elPerfilNoRellenaLosVallesQueYaEstabanMasBajos() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        int x = layout.sphereAtCell(0, 0).radius() - 1;
        int y = SphereEdgeOcean.SEA_LEVEL - DEPTH - 10;
        double air = -0.75;
        assertEquals(air, SphereEdgeOcean.transform(Mode.FLOOR, true, WIDTH_PERCENT, DEPTH,
                layout, x, y, 0, air), 1.0e-12);

        // Por debajo del suelo el perfil es tan alto que el minimo devuelve la entrada
        // tal cual: ni siquiera cambia la magnitud de una densidad solida grande.
        double deepSolid = 12.5;
        assertEquals(deepSolid, SphereEdgeOcean.transform(Mode.FLOOR, true, WIDTH_PERCENT, DEPTH,
                layout, x, y, 0, deepSolid), "el perfil no debe recortar por arriba");
    }

    /** En el borde interior de la banda el valor original se conserva casi intacto. */
    @Test
    void laTransicionHaciaElInteriorEsGradual() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        double sInner = 1.0 - WIDTH_PERCENT / 100.0;
        int x = (int) Math.ceil(sInner * layout.sphereAtCell(0, 0).radius());
        double weight = SphereEdgeOcean.weight(layout, x, config.centerY(), 0, WIDTH_PERCENT / 100.0);
        assertTrue(weight < 0.1, "el peso debe empezar cerca de 0: " + weight);
    }

    // ------------------------------------------------------------------
    // Modo clima
    // ------------------------------------------------------------------

    @Test
    void elClimaSoloBajaLaContinentalnessDentroDeLaBanda() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        int radius = layout.sphereAtCell(0, 0).radius();

        assertEquals(-0.25 - SphereEdgeOcean.CLIMATE_BIAS, SphereEdgeOcean.transform(Mode.CLIMATE, true,
                        WIDTH_PERCENT, DEPTH, layout, radius - 1, config.centerY(), 0, -0.25), 1.0e-12,
                "contra el vidrio el sesgo es maximo");

        double inside = 0.3;
        assertEquals(inside, SphereEdgeOcean.transform(Mode.CLIMATE, true, WIDTH_PERCENT, DEPTH,
                layout, 0, config.centerY(), 0, inside), 1.0e-12, "en el centro no debe cambiar");

        // Monotono hacia el borde dentro de la esfera. En s = 1 el peso vuelve a 0, de modo
        // que a partir del cascaron la continentalness recupera su valor original; ese
        // punto esta fuera del terreno y su bioma lo sustituye el wrapper por the_void.
        double previous = Double.POSITIVE_INFINITY;
        for (int x = 0; x <= radius - 1; x++) {
            double value = SphereEdgeOcean.transform(Mode.CLIMATE, true, WIDTH_PERCENT, DEPTH,
                    layout, x, config.centerY(), 0, inside);
            assertTrue(value <= previous + 1.0e-12, "el clima no puede subir hacia el borde en x=" + x);
            previous = value;
        }
    }

    @Test
    void elClimaAplicaElSesgoProporcionalAlPeso() {
        Config config = CONFIGS.get(0);
        SphereLayout layout = layout(config);
        Random random = new Random(11L);
        for (int i = 0; i < 5_000; i++) {
            int x = random.nextInt(300) - 150;
            int z = random.nextInt(300) - 150;
            int y = random.nextInt(400) - 64;
            double value = random.nextDouble() * 4 - 2;
            double weight = SphereEdgeOcean.weight(layout, x, y, z, WIDTH_PERCENT / 100.0);
            double expected = value - weight * SphereEdgeOcean.CLIMATE_BIAS;
            assertEquals(expected, SphereEdgeOcean.transform(Mode.CLIMATE, true, WIDTH_PERCENT, DEPTH,
                    layout, x, y, z, value), 1.0e-12);
        }
    }
}
