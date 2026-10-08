package cn.mlus.biosphereworlds;

import cn.mlus.biosphereworlds.config.SphereConfig;
import cn.mlus.biosphereworlds.registry.ModCarvers;
import cn.mlus.biosphereworlds.registry.ModDensityFunctions;
import cn.mlus.biosphereworlds.registry.ModFeature;
import cn.mlus.biosphereworlds.registry.ModBiomeSources;
import cn.mlus.biosphereworlds.registry.SphereBiomeModifierSerializers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BiosphereWorlds.MODID)
public class BiosphereWorlds {
    public static final String MODID = "biosphereworlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public BiosphereWorlds(IEventBus modEventBus, ModContainer modContainer) {
        SphereConfig.setup(modContainer);
        ModDensityFunctions.DENSITY_FUNCTIONS.register(modEventBus);
        ModCarvers.CARVERS.register(modEventBus);
        ModFeature.FEATURES.register(modEventBus);
        ModBiomeSources.BIOME_SOURCES.register(modEventBus);
        SphereBiomeModifierSerializers.SERIALIZERS.register(modEventBus);
    }

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
