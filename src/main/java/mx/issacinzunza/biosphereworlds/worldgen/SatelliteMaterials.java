package mx.issacinzunza.biosphereworlds.worldgen;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Materiales de una luna; deja la geometria y el orden aritmetico en cada escritor. */
public final class SatelliteMaterials {
    private final long seed;
    private final SatelliteLayers.Layers layers;
    private final SatelliteLayers.Mineral mineral;
    private final BlockState shell;
    private final BlockState ore;

    public SatelliteMaterials(SphereLayout.Satellite satellite, BlockState shell,
                              List<SatelliteLayers.Mineral> minerals) {
        this.seed = satellite.seed();
        this.layers = SatelliteLayers.layers(satellite.radius());
        this.mineral = layers.hasCore() ? SatelliteLayers.selectMineral(seed, minerals) : null;
        this.shell = shell;
        this.ore = mineral == null ? Blocks.STONE.defaultBlockState()
                : BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mineral.blockId())).defaultBlockState();
    }

    /** El llamador comprueba primero que el punto pertenece a la luna. */
    public BlockState stateAt(double squaredDistance, int x, int y, int z) {
        return switch (SatelliteLayers.regionAt(layers, squaredDistance)) {
            case SHELL -> shell;
            case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case CORE -> mineral != null && SatelliteLayers.isOre(seed, x, y, z, mineral.densityPermille())
                    ? ore : Blocks.STONE.defaultBlockState();
        };
    }
}
