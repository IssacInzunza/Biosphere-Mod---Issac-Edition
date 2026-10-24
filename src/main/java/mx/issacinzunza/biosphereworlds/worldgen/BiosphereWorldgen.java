package mx.issacinzunza.biosphereworlds.worldgen;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
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

    /** Solo el servidor escribe barreras; se identifica el generador antes de consultar opciones o geometria. */
    public static boolean preserveWaterShell(LevelAccessor level, BlockPos pos, FluidState water) {
        if (!(level instanceof ServerLevel server)) return false;
        var generator = server.getChunkSource().getGenerator();
        boolean upper, lower, boundary;
        double centerY;
        if (generator instanceof SolarChunkGenerator solar) {
            var settings = solar.solarSettings().general();
            upper = settings.upperShellEnabled();
            lower = settings.lowerShellEnabled();
            if ((upper && lower) || !water.is(FluidTags.WATER)) return false;
            var layout = solar.layout();
            var body = layout.bodyAt(pos.getX(), pos.getY(), pos.getZ(), 0);
            if (body == null) return false;
            centerY = body.y();
            boundary = layout.shellBoundary(body, pos.getX(), pos.getY(), pos.getZ());
        } else {
            if (!isBiosphereGenerator(generator)) return false;
            var world = classicWorld(generator);
            upper = world == null ? SphereConfig.UPPER_SHELL_ENABLED.get() : world.settings().upperShellEnabled();
            lower = world == null ? SphereConfig.LOWER_SHELL_ENABLED.get() : world.settings().lowerShellEnabled();
            if ((upper && lower) || !water.is(FluidTags.WATER)) return false;
            boolean cap = world == null ? SphereConfig.CLOSE_OPEN_TOP.get() : world.settings().closeOpenTop();
            var sphere = classicLayout(generator).mainShellBandAt(pos.getX(), pos.getY(), pos.getZ(),
                    cap, server.getMaxBuildHeight() - 1, false);
            if (sphere == null) return false;
            centerY = sphere.centerY();
            boundary = true;
        }
        return SphereLayout.preserveShellForWater(true, true, boundary, pos.getY(), centerY, upper, lower)
                && server.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
    }
}
