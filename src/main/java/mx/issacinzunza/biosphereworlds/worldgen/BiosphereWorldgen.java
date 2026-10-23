package mx.issacinzunza.biosphereworlds.worldgen;

import net.minecraft.world.level.levelgen.NoiseChunk;

public final class BiosphereWorldgen {
    private BiosphereWorldgen() {
    }

    public static ClassicWorldSettings.Bound classicWorld(Object owner) {
        return owner instanceof ClassicWorldSettings.Access access ? access.biosphereworlds$classicWorld() : null;
    }

    /** El consumidor ya ha comprobado el preset; null mantiene la ruta legacy. */
    public static SphereLayout classicLayout(Object owner) {
        var world = classicWorld(owner);
        return world == null ? SphereLayout.current() : world.layout();
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
