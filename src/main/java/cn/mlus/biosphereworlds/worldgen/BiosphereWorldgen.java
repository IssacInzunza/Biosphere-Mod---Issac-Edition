package cn.mlus.biosphereworlds.worldgen;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;

public final class BiosphereWorldgen {
    private static final java.util.Set<Aquifer.NoiseBasedAquifer> BIOSPHERE_AQUIFERS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

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

    public static void markAquifer(Aquifer.NoiseBasedAquifer aquifer) {
        BIOSPHERE_AQUIFERS.add(aquifer);
    }

    public static boolean isBiosphereAquifer(Aquifer.NoiseBasedAquifer aquifer) {
        return BIOSPHERE_AQUIFERS.contains(aquifer);
    }

    public static boolean isBiosphereChunk(Object chunk) {
        return chunk instanceof BiosphereChunkAccess access && access.biosphereworlds$isBiosphere();
    }
}
