package mx.issacinzunza.biosphereworlds.worldgen;

import net.minecraft.world.level.levelgen.NoiseChunk;

public final class BiosphereWorldgen {
    private BiosphereWorldgen() {
    }

    public static void markBiomeSource(Object source) {
        if (source instanceof BiosphereBiomeSourceAccess access) {
            access.biosphereworlds$setBiosphereBiomeSource(true);
        }
    }

    public static boolean isBiosphereBiomeSource(Object source) {
        return source instanceof BiosphereBiomeSourceAccess access && access.biosphereworlds$isBiosphereBiomeSource();
    }

    public static boolean isBiosphereGenerator(Object generator) {
        return generator instanceof BiosphereGeneratorAccess access && access.biosphereworlds$isBiosphereGenerator();
    }

    public static void markNoiseChunk(NoiseChunk noiseChunk) {
        ((BiosphereNoiseChunkAccess) noiseChunk).biosphereworlds$setBiosphere(true);
    }

    public static boolean isBiosphereNoiseChunk(NoiseChunk noiseChunk) {
        return ((BiosphereNoiseChunkAccess) noiseChunk).biosphereworlds$isBiosphere();
    }

    public static boolean isBiosphereChunk(Object chunk) {
        return chunk instanceof BiosphereChunkAccess access && access.biosphereworlds$isBiosphere();
    }
}
