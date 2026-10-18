package mx.issacinzunza.biosphereworlds.carver;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.event.SatelliteMineralCatalog;
import mx.issacinzunza.biosphereworlds.config.SphereConfig.BedrockMode;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Evita repetir el log de diagnostico de la celda (0,0) en cada chunk. */
    private static final AtomicBoolean LOGGED_SATELLITES_CELL_ZERO = new AtomicBoolean();

    public SphereCarver(Codec<SphereCarverConfig> codec) {
        super(codec);
    }

    @Override
    public boolean carve(@NotNull CarvingContext context, @NotNull SphereCarverConfig config,
                         @NotNull ChunkAccess chunk, @NotNull Function<BlockPos, Holder<Biome>> biomeAccessor,
                         @NotNull RandomSource random, @NotNull Aquifer aquifer, @NotNull ChunkPos chunkPos,
                         @NotNull CarvingMask carvingMask) {

        boolean biosphereChunk = BiosphereWorldgen.isBiosphereChunk(chunk);
        if (!biosphereChunk) {
            return false;
        }

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

                // Satelites de esta celda. El bucle es independiente del de la
                // principal: un chunk que solo toca un satelite (sin tocar la
                // esfera principal) sigue enumerando esta celda gracias a
                // firstCellIndex/lastCellIndex, y aqui no hay ninguna poda por
                // distancia al centro de la principal.
                if (SphereLayout.satellitesAllowed(biosphereChunk, layout.params().satellitesEnabled())) {
                    List<SphereLayout.Satellite> satellites = layout.satellitesAtCell(gridX, gridZ);
                    for (SphereLayout.Satellite satellite : satellites) {
                        generated |= carveSatellite(chunk, satellite, blockState,
                                chunkStartX, chunkEndX, chunkStartZ, chunkEndZ);
                    }
                    if (gridX == 0 && gridZ == 0
                            && LOGGED_SATELLITES_CELL_ZERO.compareAndSet(false, true)) {
                        logSatellitesCellZero(satellites);
                    }
                }
            }
        }

        return generated;
    }

    /**
     * Pinta un satelite de la etapa 2: cascaron del mismo bloque que su principal,
     * cuerpo de piedra, capa de magma y nucleo de piedra con vetas de un unico
     * mineral. No aplica tapa, forro de bedrock ni {@code only_upper_hemisphere}:
     * los satelites son siempre esferas completas y la colocacion ya garantiza que
     * caben en los limites del mundo.
     *
     * <p>El umbral del cascaron es el mismo de la etapa 1
     * ({@code d^2 > (radius - SHELL_THICKNESS)^2}), asi que el vidrio queda
     * identico.</p>
     */
    private boolean carveSatellite(ChunkAccess chunk, SphereLayout.Satellite satellite,
                                   BlockState shellState,
                                   int chunkStartX, int chunkEndX, int chunkStartZ, int chunkEndZ) {
        double radius = satellite.radius();
        double outerRadiusSq = radius * radius;

        SatelliteLayers.Layers layers = SatelliteLayers.layers(satellite.radius());
        SatelliteLayers.Mineral mineral = layers.hasCore()
                ? SatelliteLayers.selectMineral(satellite.seed(), SatelliteMineralCatalog.candidates())
                : null;
        BlockState oreState = mineral == null
                ? null
                : BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mineral.blockId())).defaultBlockState();

        int minX = Math.max(chunkStartX, (int) Math.floor(satellite.centerX() - radius));
        int maxX = Math.min(chunkEndX, (int) Math.ceil(satellite.centerX() + radius));
        int minZ = Math.max(chunkStartZ, (int) Math.floor(satellite.centerZ() - radius));
        int maxZ = Math.min(chunkEndZ, (int) Math.ceil(satellite.centerZ() + radius));
        int minY = Math.max(chunk.getMinBuildHeight(), (int) Math.floor(satellite.centerY() - radius));
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, (int) Math.ceil(satellite.centerY() + radius));

        BlockState bodyState = Blocks.STONE.defaultBlockState();
        BlockState magmaState = Blocks.MAGMA_BLOCK.defaultBlockState();
        boolean generated = false;

        for (int x = minX; x <= maxX; x++) {
            double dx = x - satellite.centerX();
            double dx2 = dx * dx;
            if (dx2 > outerRadiusSq) {
                continue;
            }
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z - satellite.centerZ();
                double horizontalDistSq = dx2 + dz * dz;
                if (horizontalDistSq > outerRadiusSq) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    double dy = y - satellite.centerY();
                    double distanceSq = horizontalDistSq + dy * dy;
                    if (distanceSq > outerRadiusSq) {
                        continue;
                    }

                    BlockState state = switch (SatelliteLayers.regionAt(layers, distanceSq)) {
                        case SHELL -> shellState;
                        case MAGMA -> magmaState;
                        case STONE -> bodyState;
                        case CORE -> SatelliteLayers.isOre(satellite.seed(), x, y, z,
                                mineral.densityPermille()) ? oreState : bodyState;
                    };
                    chunk.setBlockState(new BlockPos(x, y, z), state, false);
                    generated = true;
                }
            }
        }

        return generated;
    }

    /** Log DEBUG con las capas, el mineral y el mineral contado de la celda (0,0). */
    private static void logSatellitesCellZero(List<SphereLayout.Satellite> satellites) {
        StringBuilder message = new StringBuilder("satellites cell=(0,0) count=")
                .append(satellites.size());
        for (SphereLayout.Satellite satellite : satellites) {
            SatelliteLayers.Layers layers = SatelliteLayers.layers(satellite.radius());
            message.append(" [x=").append(format(satellite.centerX()))
                    .append(" y=").append(format(satellite.centerY()))
                    .append(" z=").append(format(satellite.centerZ()))
                    .append(" r=").append(satellite.radius())
                    .append(" inner=").append(layers.innerRadius())
                    .append(" magmaOuter=").append(layers.magmaOuterRadius());
            if (layers.hasCore()) {
                SatelliteLayers.Mineral mineral = SatelliteLayers.selectMineral(
                        satellite.seed(), SatelliteMineralCatalog.candidates());
                int oreBlocks = SatelliteLayers.countOre(satellite.seed(), layers,
                        mineral.densityPermille(),
                        satellite.centerX(), satellite.centerY(), satellite.centerZ());
                message.append(" core=").append(layers.coreRadius())
                        .append(" mineral=").append(mineral.blockId())
                        .append(" oreBlocks=").append(oreBlocks);
            } else {
                message.append(" core=none mineral=none oreBlocks=0");
            }
            message.append(']');
        }
        BiosphereWorlds.LOGGER.debug(message.toString());
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
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