package mx.issacinzunza.biosphereworlds;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.registry.ModCarvers;
import mx.issacinzunza.biosphereworlds.registry.ModDensityFunctions;
import mx.issacinzunza.biosphereworlds.registry.ModFeature;
import mx.issacinzunza.biosphereworlds.registry.ModBiomeAccess;
import mx.issacinzunza.biosphereworlds.registry.ModBiomeSources;
import mx.issacinzunza.biosphereworlds.registry.SphereBiomeModifierSerializers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BiosphereWorlds.MODID)
public class BiosphereWorlds {
    public static final String MODID = "biosphereworlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public BiosphereWorlds(IEventBus modEventBus, ModContainer modContainer) {
        SphereConfig.setup(modContainer);
        mx.issacinzunza.biosphereworlds.worldgen.solar.SolarWorldgen.register(modEventBus);
        ModDensityFunctions.DENSITY_FUNCTIONS.register(modEventBus);
        ModCarvers.CARVERS.register(modEventBus);
        ModFeature.FEATURES.register(modEventBus);
        ModBiomeSources.BIOME_SOURCES.register(modEventBus);
        SphereBiomeModifierSerializers.SERIALIZERS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(ModBiomeAccess::onServerAboutToStart);
    }

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
