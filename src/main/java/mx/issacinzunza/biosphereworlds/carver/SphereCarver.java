package mx.issacinzunza.biosphereworlds.carver;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.config.SphereConfig.BedrockMode;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarverDebugSettings;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class SphereCarver extends WorldCarver<SphereCarver.SphereCarverConfig> {

    /** Grosor del forro de bedrock del modo shell, en bloques. */
    public static final int BEDROCK_LINER_THICKNESS = 3;

    /**
     * Capas sobre el minimo del mundo que barre el modo none. El JSON de
     * surface_rule usa above_bottom 0 y 5, que resuelven a minY y minY + 5, y la
     * condicion es verdadera en minY y falsa en minY + 5, asi que la regla solo
     * puede rellenar minY..minY + 4; la capa extra es margen.
     */
    private static final int BEDROCK_FLOOR_BAND_TOP = 5;

    public SphereCarver(Codec<SphereCarverConfig> codec) {
        super(codec);
    }

    @Override
    public boolean carve(@NotNull CarvingContext context, @NotNull SphereCarverConfig config,
                         @NotNull ChunkAccess chunk, @NotNull Function<BlockPos, Holder<Biome>> biomeAccessor,
                         @NotNull RandomSource random, @NotNull Aquifer aquifer, @NotNull ChunkPos chunkPos,
                         @NotNull CarvingMask carvingMask) {

        SphereLayout layout = SphereLayout.current();
        boolean closeOpenTop = SphereConfig.CLOSE_OPEN_TOP.get();
        int topCapY = chunk.getMaxBuildHeight() - 1;

        // bedrock_mode. Con floor no se activa ninguna rama nueva. El forro solo
        // existe con hemisphere completo: con only_upper_hemisphere no hay mitad
        // inferior que forrar y shell se comporta exactamente como floor.
        BedrockMode bedrockMode = SphereConfig.BEDROCK_MODE.get();
        boolean bedrockLiner = bedrockMode == BedrockMode.SHELL && !SphereConfig.ONLY_UPPER_HEMISPHERE.get();
        BlockState bedrockState = bedrockLiner ? Blocks.BEDROCK.defaultBlockState() : null;
        if (bedrockMode == BedrockMode.NONE) {
            replaceBedrockFloor(chunk);
        }

        int chunkStartX = chunkPos.getMinBlockX();
        int chunkStartZ = chunkPos.getMinBlockZ();
        int chunkEndX = chunkStartX + 15;
        int chunkEndZ = chunkStartZ + 15;

        boolean generated = false;

        int gridXStart = layout.firstCellIndex(chunkStartX);
        int gridXEnd = layout.lastCellIndex(chunkEndX);
        int gridZStart = layout.firstCellIndex(chunkStartZ);
        int gridZEnd = layout.lastCellIndex(chunkEndZ);

        for (int gridX = gridXStart; gridX <= gridXEnd; gridX++) {
            for (int gridZ = gridZStart; gridZ <= gridZEnd; gridZ++) {

                SphereLayout.Sphere sphere = layout.sphereAtCell(gridX, gridZ);

                double centerX = sphere.centerX();
                double centerZ = sphere.centerZ();
                int centerY = (int) sphere.centerY();

                int radius = sphere.radius();
                if (SphereConfig.ONLY_UPPER_HEMISPHERE.get())
                    radius--;

                BlockState blockState = getSphereBlockState(gridX, gridZ);

                int minY = Math.max(chunk.getMinBuildHeight(), centerY - radius - 1);
                int maxY = Math.min(chunk.getMaxBuildHeight(), centerY + radius + 1);

                double shellThickness = SphereLayout.SHELL_THICKNESS;
                double innerRadiusSq = (radius - shellThickness) * (radius - shellThickness);
                double outerRadiusSq = radius * radius;

                // Forro (modo shell): las tres capas pegadas por dentro al
                // cascaron. Fuera de shell no se calcula nada.
                double linerInnerRadiusSq = 0.0;
                if (bedrockLiner) {
                    double linerInnerRadius = radius - shellThickness - BEDROCK_LINER_THICKNESS;
                    if (linerInnerRadius < 0.0) linerInnerRadius = 0.0;
                    linerInnerRadiusSq = linerInnerRadius * linerInnerRadius;
                }

                for (int x = chunkStartX; x <= chunkEndX; x++) {
                    double dx = x - centerX;
                    double dx2 = dx * dx;

                    if (dx2 > outerRadiusSq) {
                        continue;
                    }

                    for (int z = chunkStartZ; z <= chunkEndZ; z++) {
                        double dz = z - centerZ;
                        double dz2 = dz * dz;
                        double horizontalDistSq = dx2 + dz2;

                        if (horizontalDistSq > outerRadiusSq) {
                            continue;
                        }

                        for (int y = minY; y <= maxY; y++) {
                            double dy = y - centerY;
                            double distanceSq = horizontalDistSq + dy * dy;

                            // Cascaron historico. Con close_open_top activo la
                            // misma condicion rellena ademas el disco completo en
                            // el Y colocable mas alto, cerrando el hueco que deja
                            // una esfera cortada por el limite de construccion.
                            if (distanceSq <= outerRadiusSq
                                    && (distanceSq >= innerRadiusSq || (closeOpenTop && y == topCapY))) {
                                if(!SphereConfig.ONLY_UPPER_HEMISPHERE.get() || canReplaceBlock(config, chunk.getBlockState(new BlockPos(x, y, z)))) {
                                    if(SphereConfig.ONLY_UPPER_HEMISPHERE.get() && (y < 55 && !chunk.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)))
                                        continue;
                                    BlockPos pos = new BlockPos(x, y, z);
                                    chunk.setBlockState(pos, blockState, false);
                                    generated = true;
                                }
                            }
                            // Forro de bedrock del modo shell: mitad inferior
                            // (y <= centerY) y estrictamente por dentro del
                            // cascaron, que nunca se toca.
                            else if (bedrockLiner
                                    && dy <= 0.0
                                    && distanceSq >= linerInnerRadiusSq
                                    && distanceSq < innerRadiusSq) {
                                chunk.setBlockState(new BlockPos(x, y, z), bedrockState, false);
                                generated = true;
                            }
                        }
                    }
                }
            }
        }

        return generated;
    }

    /**
     * Modo none: la regla {@code bedrock_floor} de la surface_rule pone bedrock
     * en las primeras capas del mundo. Las reglas de superficie corren en el
     * estado SURFACE y los carvers en el estado CARVERS, asi que en este punto
     * esa bedrock ya esta puesta y se puede sustituir sin tocar el JSON. Solo se
     * sustituye bedrock: el aire y el agua no se tocan. El bloque que la regla
     * reemplazo era {@code default_block} (piedra), pero aqui se usa deepslate.
     */
    private static void replaceBedrockFloor(@NotNull ChunkAccess chunk) {
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        int floorY = chunk.getMinBuildHeight();
        int bandTop = Math.min(chunk.getMaxBuildHeight() - 1, floorY + BEDROCK_FLOOR_BAND_TOP);
        ChunkPos chunkPos = chunk.getPos();

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = floorY; y <= bandTop; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (chunk.getBlockState(pos).is(Blocks.BEDROCK)) {
                        chunk.setBlockState(pos, deepslate, false);
                    }
                }
            }
        }
    }

    @Override
    protected boolean canReplaceBlock(@NotNull SphereCarverConfig config, @NotNull BlockState state) {
        return state.is(BlockTags.AIR) || state.is(Blocks.WATER);
    }

    @Override
    public boolean isStartChunk(@NotNull SphereCarverConfig config, @NotNull RandomSource random) {
        return true;
    }

    public static BlockState getSphereBlockState(int gridX, int gridZ) {
        java.util.List<? extends String> sphereBlocks = SphereConfig.SPHERE_BLOCK.get();
        if (sphereBlocks.isEmpty()) return Blocks.AIR.defaultBlockState();
        int blockIndex = Math.abs(gridX * 31 + gridZ) % sphereBlocks.size();
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.parse(sphereBlocks.get(blockIndex))).defaultBlockState();
    }

    public static class SphereCarverConfig extends CarverConfiguration {
        public static final Codec<SphereCarverConfig> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        CarverConfiguration.CODEC.forGetter(c -> c),
                        Codec.INT.fieldOf("spacing").forGetter(c -> c.spacing),
                        Codec.INT.fieldOf("radius").forGetter(c -> c.radius),
                        Codec.INT.fieldOf("center_y").forGetter(c -> c.centerY),
                        Codec.DOUBLE.fieldOf("tolerance").forGetter(c -> c.tolerance),
                        BlockState.CODEC.fieldOf("block_state").forGetter(c -> c.blockState)
                ).apply(instance, SphereCarverConfig::new)
        );

        public final int spacing;
        public final int radius;
        public final int centerY;
        public final double tolerance;
        public final BlockState blockState;

        public SphereCarverConfig(float probability, HeightProvider y, FloatProvider yScale,
                                  VerticalAnchor lavaLevel, CarverDebugSettings debugSettings,
                                  int spacing, int radius, int centerY,
                                  double tolerance, BlockState blockState) {
            super(probability, y, yScale, lavaLevel, debugSettings, HolderSet.direct(Holder.direct(Blocks.AIR)));
            this.spacing = spacing;
            this.radius = radius;
            this.centerY = centerY;
            this.tolerance = tolerance;
            this.blockState = blockState;
        }

        public SphereCarverConfig(CarverConfiguration baseConfig, int spacing, int radius,
                                  int centerY, double tolerance, BlockState blockState) {
            this(baseConfig.probability, baseConfig.y, baseConfig.yScale, baseConfig.lavaLevel,
                    baseConfig.debugSettings, spacing, radius, centerY, tolerance, blockState);
        }
    }
}